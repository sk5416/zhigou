"""Install real dev.7, seed learning.db, then install current APK with -r (no uninstall)."""
import json
import subprocess
import sys
from pathlib import Path
from run_process_recovery import adb, PACKAGE, RUNNER

def phase(name, version):
    text = adb('shell', 'am', 'instrument', '-w', '-e', 'class', PACKAGE + '.UpgradeTest',
               '-e', 'upgradePhase', name, '-e', 'expectedVersion', version, RUNNER)
    print(text, flush=True)
    if 'OK (1 test)' not in text or (name == 'verify' and 'upgrade_verified=true' not in text):
        raise RuntimeError(f'Upgrade {name} failed')

def main():
    old, current, test = map(Path, sys.argv[1:])
    for path in (old, current, test):
        if not path.is_file(): raise FileNotFoundError(path)
    for path in (old, test):
        result = subprocess.run(['adb', 'install', '-r', str(path)], capture_output=True, text=True, check=True)
        if 'Success' not in result.stdout: raise RuntimeError(result.stdout + result.stderr)
    uid_before = adb('shell', 'stat', '-c', '%u', '/data/data/' + PACKAGE)
    phase('seed', '7')
    result = subprocess.run(['adb', 'install', '-r', str(current)], capture_output=True, text=True, check=True)
    if 'Success' not in result.stdout: raise RuntimeError(result.stdout + result.stderr)
    uid_after = adb('shell', 'stat', '-c', '%u', '/data/data/' + PACKAGE)
    if uid_before != uid_after: raise RuntimeError('Application UID changed during covering install')
    phase('verify', '8')
    path = Path('.artifacts/upgrade-report.json'); path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps({'status': 'passed', 'fromVersionCode': 7, 'toVersionCode': 8, 'uidBefore': uid_before, 'uidAfter': uid_after,
        'install': 'adb install -r; no uninstall between seed and verify', 'databaseVersions': [1, 1],
        'scope': 'actual dev.7 APK to dev.8 APK, reading/bookmarks/sessions/attempts/mistakes/preferences',
        'notCovered': ['Room schema migration (no schema change)', 'release signing key', 'physical device']}, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__': main()
