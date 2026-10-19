"""Headless, instance-scoped Minecraft supervisor (Python 3.11+, no packages)."""
import argparse
import ctypes
from ctypes import wintypes
import datetime
import json
import math
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import threading
import time
import uuid

EXIT_CONFIG, EXIT_BUSY, EXIT_START, EXIT_TIMEOUT, EXIT_COMMAND = 2, 3, 4, 5, 6


def stamp():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def identity(pid):
    """PID plus kernel creation time, so a reused PID cannot identify our process."""
    if os.name == 'nt':
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
        kernel.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        handle = kernel.OpenProcess(0x1000, False, int(pid))
        if not handle:
            if ctypes.get_last_error() == 5:
                raise PermissionError('Cannot verify process identity for PID ' + str(pid))
            return None
        try:
            created, exited, kern, user = [wintypes.FILETIME() for _ in range(4)]
            code = wintypes.DWORD()
            if not kernel.GetProcessTimes(handle, ctypes.byref(created), ctypes.byref(exited), ctypes.byref(kern), ctypes.byref(user)):
                raise OSError('Cannot read process identity')
            if not kernel.GetExitCodeProcess(handle, ctypes.byref(code)) or code.value != 259:
                return None
            return {'pid': int(pid), 'birth': (created.dwHighDateTime << 32) | created.dwLowDateTime}
        finally:
            kernel.CloseHandle(handle)
    try:
        return {'pid': int(pid), 'birth': Path('/proc', str(pid), 'stat').read_text().rsplit(')', 1)[1].split()[19]}
    except FileNotFoundError:
        return None


def alive(value):
    return bool(value and identity(value['pid']) == value)


def read_json(path):
    # Exclusive creation establishes ownership before the tiny first JSON write.
    # Readers can observe that publication window; retry it without deleting locks.
    for attempt in range(20):
        try:
            value = json.loads(path.read_text(encoding='utf-8'))
            if not isinstance(value, dict):
                raise ValueError('Expected a JSON object: ' + str(path))
            return value
        except (json.JSONDecodeError, PermissionError):
            if attempt == 19:
                raise
            time.sleep(.05)


def write_json(path, value):
    temp = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    temp.write_text(json.dumps(value, indent=2), encoding='utf-8')
    try:
        for attempt in range(20):
            try:
                temp.replace(path)
                break
            except PermissionError:
                # Windows readers or antivirus may briefly deny replacement.
                if attempt == 19:
                    raise
                time.sleep(.05)
    finally:
        temp.unlink(missing_ok=True)


def properties(root):
    return dict(line.split('=', 1) for line in (root / 'server.properties').read_text(encoding='utf-8').splitlines()
                if '=' in line and not line.startswith('#'))


def validate(root):
    if not root.is_dir() or not (root / 'testlab-instance.json').exists():
        raise ValueError('Instance must have testlab-instance.json; refusing an unowned directory')
    p = properties(root)
    if p.get('server-ip') != '127.0.0.1' or p.get('enable-rcon') != 'false' or p.get('enable-query') != 'false':
        raise ValueError('Require localhost binding and disabled RCON/query')
    if not re.search(r'(?m)^eula=true\s*$', (root / 'eula.txt').read_text(encoding='utf-8')):
        raise ValueError('EULA is not accepted; this runner never accepts it')
    with socket.socket() as test:
        test.bind(('127.0.0.1', int(p['server-port'])))


def request(root, command, timeout, probe_token=None, expected_owner_token=None):
    base = root / '.testlab'
    owner = read_json(base / 'owner.json')
    if expected_owner_token is not None and owner.get('token') != expected_owner_token:
        raise RuntimeError('Instance owner changed; refusing to send command to a different run')
    if not alive(owner.get('supervisor')):
        raise RuntimeError('Supervisor is absent; inspect status or recover stale lock')
    if '\n' in command or '\r' in command or not command.strip():
        raise ValueError('One nonempty console command per request')
    key = uuid.uuid4().hex
    target = base / 'queue' / (key + '.json')
    write_json(target, {'owner': owner['token'], 'command': command.lstrip('/'), 'created': stamp(), 'probe_token': probe_token})
    response = base / 'responses' / (key + '.json')
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if response.exists():
            result = read_json(response)
            if not result.get('ok'):
                raise RuntimeError(result.get('error', 'Command rejected'))
            return result
        if not alive(owner['supervisor']):
            raise RuntimeError('Supervisor exited before command acknowledgement')
        time.sleep(.1)
    raise TimeoutError('Command acknowledgement timeout')


def probe(args):
    """Wait for one complete GTIT lab report, rather than console-send success."""
    root = args.instance
    owner = read_json(root / '.testlab' / 'owner.json')
    run = Path(owner['run'])
    command = args.command[0] if args.command else 'gtitlab suite'
    if len(args.command) > 1 or not re.match(r'^/?gtitlab\s+(suite|run|stress|profile|refresh)(\s|$)', command):
        raise ValueError('probe requires one gtitlab suite/run/stress/profile/refresh command')
    probe_lock = root / '.testlab' / 'probe.lock'
    probe_token = uuid.uuid4().hex
    with probe_lock.open('x', encoding='utf-8') as lock_stream:
        json.dump({'token': probe_token, 'client': identity(os.getpid())}, lock_stream)
    try:
        return wait_probe(args, owner, run, command, probe_token)
    finally:
        if probe_lock.exists() and read_json(probe_lock).get('token') == probe_token:
            probe_lock.unlink()


def validate_report(report, command, required_scenarios, required_checks=()):
    """Check schema and substantive coverage; a harness-only PASS is insufficient."""
    if report.get('schema') != 1:
        raise ValueError('Unsupported lab report schema')
    parts = command.lstrip('/').split()
    expected_mode = 'suite' if parts[1] == 'refresh' else parts[1]
    if report.get('mode') != expected_mode:
        raise ValueError('Lab report mode does not match command')
    try:
        start = datetime.datetime.fromisoformat(report['startedAt'].replace('Z', '+00:00'))
        finish = datetime.datetime.fromisoformat(report['finishedAt'].replace('Z', '+00:00'))
        if start.tzinfo is None or finish.tzinfo is None or finish < start:
            raise ValueError('Invalid lab timestamps')
    except (KeyError, TypeError, AttributeError) as error:
        raise ValueError('Lab report must contain UTC start/finish timestamps') from error
    env, metrics, cases = report.get('environment'), report.get('metrics'), report.get('cases')
    if not isinstance(env, dict) or not isinstance(metrics, dict) or not isinstance(cases, list):
        raise ValueError('Lab report requires environment, metrics, and cases')
    selected = env.get('selectedScenarios')
    if not isinstance(selected, list) or not selected or any(not isinstance(item, str) or not item for item in selected) or len(set(selected)) != len(selected):
        raise ValueError('Lab report requires nonempty unique selectedScenarios')
    explicit = parts[2] if len(parts) > 2 and (parts[1] == 'run' or (parts[1] == 'profile' and not parts[2].isdigit())) else None
    if explicit is not None and selected != [explicit]:
        raise ValueError('Lab selectedScenarios does not match the requested scenario')
    missing = set(required_scenarios) - set(selected)
    if missing:
        raise ValueError('Required scenarios missing: ' + ', '.join(sorted(missing)))
    counts = {'PASS': 0, 'FAIL': 0, 'SKIP': 0}
    concrete = set()
    for case in cases:
        if not isinstance(case, dict) or case.get('status') not in counts or any(not isinstance(case.get(key), str) or not case[key] for key in ('scenario', 'check', 'detail')):
            raise ValueError('Lab case requires scenario/check/status/detail')
        counts[case['status']] += 1
        if case['status'] in ('PASS', 'FAIL'):
            concrete.add(case['scenario'])
    for key, status in [('passed', 'PASS'), ('failed', 'FAIL'), ('skipped', 'SKIP')]:
        value = metrics.get(key)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value != counts[status]:
            raise ValueError('Lab metrics disagree with concrete cases: ' + key)
    # FAIL/SKIP still return their dedicated nonzero code; strict success checks follow.
    if report.get('status') == 'PASS':
        remaining = env.get('ownedRemaining', env.get('ownedBlocksRemaining'))
        if env.get('cleanupComplete') is not True or isinstance(remaining, bool) or remaining != 0:
            raise ValueError('Lab cleanup is incomplete or owned fixtures remain')
        if counts['FAIL'] or not counts['PASS']:
            raise ValueError('Lab PASS contradicts case outcomes')
        uncovered = set(selected) - concrete
        if uncovered:
            raise ValueError('Selected scenarios have no concrete PASS/FAIL case: ' + ', '.join(sorted(uncovered)))
        passed_checks = {case['scenario'] + '/' + case['check'] for case in cases if case['status'] == 'PASS'}
        missing_checks = set(required_checks) - passed_checks
        if missing_checks:
            raise ValueError('Required PASS checks missing: ' + ', '.join(sorted(missing_checks)))
    return selected


def wait_probe(args, owner, run, command, probe_token):
    root = args.instance
    allowed = (root / 'logs' / 'gtit-testlab').resolve()
    old_reports = {entry.resolve() for entry in allowed.glob('*.json')}
    request_id = str(uuid.uuid4())
    # Byte offset isolates this command from COMPLETE records in earlier runs.
    with (run / 'console.log').open('rb') as stream:
        stream.seek(0, 2)
        request(root, command + ' --request ' + request_id, min(args.timeout, 30), probe_token, owner['token'])
        deadline = time.monotonic() + args.timeout
        pending = b''
        started_run = None
        expected_mode = 'suite' if command.lstrip('/').split()[1] == 'refresh' else command.lstrip('/').split()[1]
        while time.monotonic() < deadline:
            pending += stream.read()
            lines = pending.split(b'\n')
            pending = lines.pop()
            for line in lines:
                decoded = line.decode('utf-8', 'replace')
                rejected = re.search(r'\[GTIT-LAB\] REJECT request=(\S+) reason=(.*)', decoded)
                if rejected and rejected[1] == request_id:
                    raise RuntimeError('Lab command rejected: ' + rejected[2])
                failed_report = re.search(r'\[GTIT-LAB\] REPORT_FAILED run=(\S+)', decoded)
                if failed_report and failed_report[1] == started_run:
                    raise RuntimeError('Lab failed to write its report')
                start_match = re.search(r'\[GTIT-LAB\] START run=(\S+) mode=(\S+) tasks=(\d+) request=(\S+)', decoded)
                if start_match and started_run is None and start_match[2] == expected_mode and start_match[4] == request_id:
                    uuid.UUID(start_match[1])
                    if int(start_match[3]) < 1:
                        raise ValueError('Lab START selected no tasks')
                    started_run = start_match[1]
                match = re.search(r'\[GTIT-LAB\] COMPLETE run=(\S+) status=(PASS|FAIL|SKIP) report=(.+?)\s*$', decoded)
                if match:
                    run_id, status, report_name = match.groups()
                    if started_run is None or run_id != started_run:
                        continue
                    report_path = Path(report_name).resolve()
                    if not report_path.is_relative_to(allowed):
                        raise ValueError('Lab report path is outside this instance')
                    if report_path in old_reports:
                        raise ValueError('Refusing a report that existed before this probe')
                    if report_path.name != run_id + '.json':
                        raise ValueError('Lab report filename and run ID disagree')
                    report = read_json(report_path)
                    if str(report.get('runId')) != run_id or report.get('status') != status:
                        raise ValueError('Lab completion marker and report disagree')
                    if not isinstance(report.get('environment'), dict) or report['environment'].get('requestId') != request_id:
                        raise ValueError('Lab report requestId does not match this probe')
                    selected = validate_report(report, command, getattr(args, 'require_scenario', []), getattr(args, 'require_check', []))
                    result = {'runId': run_id, 'requestId': request_id, 'status': status, 'report': str(report_path), 'command': command, 'selectedScenarios': selected}
                    write_json(run / ('probe-' + run_id + '.json'), result)
                    print(json.dumps(result))
                    return 0 if status == 'PASS' else EXIT_COMMAND
            if not alive(owner['supervisor']):
                raise RuntimeError('Supervisor stopped before lab completion')
            time.sleep(.2)
    raise TimeoutError('GTIT lab completion timeout')


def supervise(args):
    root = args.instance.resolve()
    if (root / '.testlab' / 'owner.json').exists():
        print('Instance locked; use status, stop, or recover-lock', file=sys.stderr)
        return EXIT_BUSY
    validate(root)
    base = root / '.testlab'
    for part in ('queue', 'responses', 'runs'):
        (base / part).mkdir(parents=True, exist_ok=True)
    lock = base / 'owner.json'
    token = uuid.uuid4().hex
    owner = {'token': token, 'instance': str(root), 'supervisor': identity(os.getpid()),
             'started': stamp(), 'phase': 'starting', 'java': None}
    try:
        with lock.open('x', encoding='utf-8') as stream:
            json.dump(owner, stream)
    except FileExistsError:
        print('Instance locked; use status, stop, or recover-lock', file=sys.stderr)
        return EXIT_BUSY
    run = base / 'runs' / (datetime.datetime.now().strftime('%Y%m%dT%H%M%S') + '-' + token[:8])
    run.mkdir()
    owner['run'] = str(run)
    write_json(lock, owner)
    report = {'instance': str(root), 'started': stamp(), 'commands': [], 'status': 'starting'}
    ready = threading.Event()
    process = None
    result = 0
    started = time.monotonic()
    stop_at = None
    log = (run / 'console.log').open('w', encoding='utf-8', buffering=1)

    def reader():
        for line in process.stdout:
            log.write(line)
            if re.search(r'Done \([0-9.,]+s\)!', line):
                ready.set()

    def send(command):
        process.stdin.write(command + '\n')
        process.stdin.flush()
        report['commands'].append({'command': command, 'sent': stamp()})

    try:
        command = [args.java, '-Xms' + args.xms, '-Xmx' + args.xmx,
                   '-Dfml.readTimeout=180', '-Duser.language=en', '-Dfile.encoding=UTF-8',
                   '@java9args.txt', '-jar', 'lwjgl3ify-forgePatches.jar', 'nogui']
        process = subprocess.Popen(command, cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, encoding='utf-8', errors='replace',
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        owner['java'] = identity(process.pid)
        write_json(lock, owner)
        thread = threading.Thread(target=reader, daemon=True)
        thread.start()
        while process.poll() is None:
            now = time.monotonic()
            if ready.is_set() and owner['phase'] == 'starting':
                owner['phase'] = 'ready'
                report['ready'] = stamp()
                write_json(lock, owner)
                for entry in args.command:
                    send(entry.lstrip('/'))
                if args.stop_after_commands:
                    send('stop')
                    stop_at = now
            for entry in sorted((base / 'queue').glob('*.json')):
                try:
                    item = read_json(entry)
                    if item['owner'] != token:
                        raise ValueError('Request belongs to a previous run')
                    probe_lock = base / 'probe.lock'
                    if probe_lock.exists() and re.match(r'^gtitlab\s+(suite|run|stress|profile|refresh|reset)(\s|$)', item['command']):
                        if read_json(probe_lock).get('token') != item.get('probe_token'):
                            raise ValueError('Another probe owns the lab command channel')
                    if not ready.is_set() and item['command'].strip().lower() != 'stop':
                        raise ValueError('Server is not ready')
                    send(item['command'])
                    if item['command'].strip().lower() == 'stop':
                        stop_at = now
                        owner['phase'] = 'stopping'
                        write_json(lock, owner)
                    response = {'ok': True, 'sent': stamp(), 'run': str(run)}
                except Exception as error:
                    response = {'ok': False, 'error': str(error)}
                write_json(base / 'responses' / entry.name, response)
                entry.unlink()
            if stop_at is None and ((not ready.is_set() and now - started > args.startup_timeout)
                                    or now - started > args.max_runtime):
                result = EXIT_TIMEOUT
                report['error'] = 'Startup or maximum runtime timeout'
                send('stop')
                stop_at = now
            if stop_at is not None and now - stop_at > args.stop_timeout:
                # Only the Popen child, with its original creation identity, is eligible.
                if alive(owner['java']):
                    process.terminate()
                    report['forced_termination'] = True
                result = EXIT_TIMEOUT
                break
            time.sleep(.1)
        process.wait(timeout=30)
        thread.join(timeout=5)
        report['java_exit_code'] = process.returncode
        if not result and (process.returncode or not ready.is_set()):
            result = EXIT_START
    except KeyboardInterrupt:
        if process and process.poll() is None:
            send('stop')
            try:
                process.wait(timeout=args.stop_timeout)
            except subprocess.TimeoutExpired:
                if alive(owner.get('java')):
                    process.terminate()
                    process.wait(timeout=30)
        result = 130
    except Exception as error:
        report['error'] = str(error)
        result = EXIT_START
        if process and process.poll() is None:
            try:
                send('stop')
                process.wait(timeout=args.stop_timeout)
            except (subprocess.TimeoutExpired, OSError):
                if alive(owner.get('java')):
                    process.terminate()
                    process.wait(timeout=30)
    finally:
        log.close()
        report.update(finished=stamp(), status='passed' if result == 0 else 'failed', exit_code=result)
        write_json(run / 'report.json', report)
        write_json(base / 'last-run.json', report | {'run': str(run)})
        if lock.exists() and read_json(lock).get('token') == token:
            # Keep the lock if our Java child somehow remains alive.
            if not alive(owner.get('java')):
                lock.unlink()
        print(json.dumps({'exit_code': result, 'run': str(run)}))
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=['run', 'start', 'status', 'command', 'probe', 'stop', 'recover-lock'])
    p.add_argument('--instance', type=Path, required=True)
    p.add_argument('--java', default='java')
    p.add_argument('--xms', default='1G')
    p.add_argument('--xmx', default='6G')
    p.add_argument('--startup-timeout', type=float, default=900)
    p.add_argument('--stop-timeout', type=float, default=120)
    p.add_argument('--max-runtime', type=float, default=3600)
    p.add_argument('--timeout', type=float, default=30)
    p.add_argument('--command', action='append', default=[])
    p.add_argument('--require-scenario', action='append', default=[])
    p.add_argument('--require-check', action='append', default=[], help='Required PASS case in scenario/check form')
    p.add_argument('--stop-after-commands', action='store_true')
    args = p.parse_args()
    args.instance = args.instance.resolve()
    try:
        base = args.instance / '.testlab'
        lock = base / 'owner.json'
        if args.action == 'run':
            return supervise(args)
        if args.action == 'start':
            if lock.exists():
                raise FileExistsError('Instance locked')
            validate(args.instance)
            base.mkdir(exist_ok=True)
            command = [sys.executable, str(Path(__file__).resolve()), 'run', *sys.argv[2:]]
            with (base / 'supervisor.log').open('a', encoding='utf-8') as output:
                child = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT,
                                         creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            deadline = time.monotonic() + args.startup_timeout + 5
            while time.monotonic() < deadline:
                if lock.exists():
                    owner = read_json(lock)
                    if owner.get('supervisor', {}).get('pid') == child.pid and owner['phase'] == 'ready':
                        print(json.dumps(owner))
                        return 0
                if child.poll() is not None:
                    return child.returncode or EXIT_START
                time.sleep(.2)
            return EXIT_TIMEOUT
        if args.action == 'status':
            if not lock.exists():
                print(json.dumps({'phase': 'stopped', 'last_run': read_json(base / 'last-run.json') if (base / 'last-run.json').exists() else None}))
            else:
                owner = read_json(lock)
                print(json.dumps(owner | {'supervisor_alive': alive(owner.get('supervisor')), 'java_alive': alive(owner.get('java'))}))
            return 0
        if args.action == 'recover-lock':
            if lock.exists():
                owner = read_json(lock)
                if alive(owner.get('supervisor')) or alive(owner.get('java')):
                    raise FileExistsError('Owner or Java child still alive; refusing recovery')
                lock.rename(base / ('stale-' + uuid.uuid4().hex + '.json'))
            probe_lock = base / 'probe.lock'
            if probe_lock.exists():
                if alive(read_json(probe_lock).get('client')):
                    raise FileExistsError('Probe client still alive; refusing recovery')
                probe_lock.rename(base / ('stale-probe-' + uuid.uuid4().hex + '.json'))
            return 0
        if args.action == 'probe':
            return probe(args)
        if args.action == 'command' and len(args.command) != 1:
            raise ValueError('command requires exactly one --command')
        owner = read_json(lock)
        print(json.dumps(request(args.instance, 'stop' if args.action == 'stop' else args.command[0], args.timeout, expected_owner_token=owner['token'])))
        if args.action == 'stop':
            deadline = time.monotonic() + args.stop_timeout + 35
            while lock.exists() and time.monotonic() < deadline:
                time.sleep(.2)
            if lock.exists():
                return EXIT_TIMEOUT
            last = read_json(base / 'last-run.json')
            if last.get('run') != owner.get('run'):
                raise ValueError('Shutdown report does not belong to the stopped run')
            code = last.get('exit_code')
            if isinstance(code, bool) or not isinstance(code, int):
                raise ValueError('Shutdown report lacks an exit code')
            return code
        return 0
    except FileExistsError as error:
        print(str(error), file=sys.stderr)
        return EXIT_BUSY
    except TimeoutError as error:
        print(str(error), file=sys.stderr)
        return EXIT_TIMEOUT
    except (OSError, ValueError, RuntimeError, KeyError) as error:
        print(str(error), file=sys.stderr)
        return EXIT_CONFIG


if __name__ == '__main__':
    raise SystemExit(main())
