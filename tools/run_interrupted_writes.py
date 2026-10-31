"""Stop a disposable emulator app at observed write checkpoints, then verify atomic recovery."""
import json
import re
import subprocess
import time
from pathlib import Path
from run_process_recovery import adb, PACKAGE, RUNNER

TEST = PACKAGE + '.InterruptedWriteTest'
POINTS = ['ATTEMPT_INSERTED', 'RESTORE_DATABASE_COMMITTED', 'RESTORE_PREFERENCES_COMMITTED']

def command(phase, point):
    return ['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', TEST,
            '-e', 'writePhase', phase, '-e', 'writePoint', point, RUNNER]

def main():
    results = []
    for point in POINTS:
        adb('shell', 'run-as', PACKAGE, 'rm', '-f', 'files/write-test.marker.json')
        with subprocess.Popen(command('interrupt', point), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) as child:
            try:
                deadline = time.monotonic() + 45
                marker = None
                while time.monotonic() < deadline and child.poll() is None:
                    raw = adb('shell', 'run-as', PACKAGE, 'cat', 'files/write-test.marker.json', check=False)
                    if raw.startswith('{'):
                        marker = json.loads(raw)
                        break
                    time.sleep(.25)
                if not marker or marker['point'] != point:
                    raise RuntimeError(f'{point}: checkpoint not observed')
                pid = adb('shell', 'pidof', PACKAGE)
                if pid != str(marker['pid']):
                    raise RuntimeError('Checkpoint PID does not match live app')
                adb('shell', 'am', 'force-stop', PACKAGE)
                if adb('shell', 'pidof', PACKAGE, check=False):
                    raise RuntimeError('App still alive after stop')
                child.communicate(timeout=15)
            finally:
                if child.poll() is None:
                    adb('shell', 'am', 'force-stop', PACKAGE)
                    child.kill(); child.communicate()
        output = subprocess.run(command('verify', point), text=True, capture_output=True, timeout=60, check=True).stdout
        print(output, flush=True)
        match = re.search(r'write_verified_pid=(\d+)', output)
        if 'OK (1 test)' not in output or f'write_verified_point={point}' not in output or not match or match.group(1) == pid:
            raise RuntimeError(f'{point}: new-process recovery failed')
        results.append({'point': point, 'stoppedPid': int(pid), 'verifiedPid': int(match.group(1))})
    path = Path('.artifacts/interrupted-writes-report.json'); path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps({'status': 'passed', 'scope': 'observed transaction/restore checkpoints followed by Android force-stop', 'cases': results,
        'notCovered': ['random physical power loss', 'storage hardware failure', 'OS low-memory policy']}, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
