"""Run on a disposable Android emulator after installing app and test APKs.

Checks completed repository writes across an actual Android force-stop, not an
interrupt in the middle of a write or restoration of the whole activity stack.
"""
import subprocess
import json
import re
from pathlib import Path

PACKAGE = 'com.sk5416.zhigou'
RUNNER = PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'
TEST = PACKAGE + '.ProcessRecoveryTest'

def adb(*args, check=True):
    result = subprocess.run(['adb', *args], text=True, capture_output=True, check=check)
    return result.stdout.strip()

def instrument(phase, *extra):
    output = adb('shell', 'am', 'instrument', '-w', '-e', 'class', TEST,
                 '-e', 'recoveryPhase', phase, *extra, RUNNER)
    print(output, flush=True)
    # am instrument can exit zero even when a test fails.
    if 'OK (1 test)' not in output or 'FAILURES!!!' in output:
        raise RuntimeError(f'{phase}: instrumentation did not pass')
    return output

def main():
    instrument('seed')
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    live = adb('shell', 'pidof', PACKAGE)
    if not live.isdecimal():
        raise RuntimeError('Expected one running application process')
    adb('shell', 'am', 'force-stop', PACKAGE)
    if adb('shell', 'pidof', PACKAGE, check=False):
        raise RuntimeError('Application process is still running after force-stop')
    print(f'Confirmed Android stopped pid={live}; verifying committed learning in a new process', flush=True)
    verified = instrument('verify', '-e', 'killedPid', live)
    match = re.search(r'recovery_verified_pid=(\d+)', verified)
    if not match or match.group(1) == live:
        raise RuntimeError('Missing evidence of verification in a different process')
    report = Path('.artifacts/process-recovery-report.json')
    report.parent.mkdir(exist_ok=True)
    report.write_text(json.dumps({'status': 'passed', 'stoppedPid': int(live), 'verifiedPid': int(match.group(1)),
        'scope': 'committed repository records and preferences across Android force-stop; dedicated database fixture',
        'notCovered': ['in-flight writes', 'activity stack restoration', 'OS low-memory kill', 'real device']}, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
