"""Install real dev.8, seed learning.db, then install current APK with -r (no uninstall)."""
import json
import re
import subprocess
import sys
from pathlib import Path
from run_process_recovery import adb, PACKAGE, RUNNER

def package_uid():
    # Query PackageManager rather than traversing app-private directories as shell.
    output = adb('shell', 'cmd', 'package', 'list', 'packages', '-U', '--user', '0', PACKAGE)
    matches = re.findall(r'^package:' + re.escape(PACKAGE) + r' uid:(\d+)\s*$', output, re.MULTILINE)
    if len(matches) != 1: raise RuntimeError('Expected exactly one installed application UID')
    return matches[0]

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
    uid_before = package_uid()
    phase('seed', '8')
    result = subprocess.run(['adb', 'install', '-r', str(current)], capture_output=True, text=True, check=True)
    if 'Success' not in result.stdout: raise RuntimeError(result.stdout + result.stderr)
    uid_after = package_uid()
    if uid_before != uid_after: raise RuntimeError('Application UID changed during covering install')
    phase('verify', '9')
    path = Path('.artifacts/upgrade-report.json'); path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps({'status': 'passed', 'fromVersionCode': 8, 'toVersionCode': 9, 'uidBefore': uid_before, 'uidAfter': uid_after,
        'install': 'adb install -r; no uninstall between seed and verify', 'uidSource': 'PackageManager list packages -U --user 0', 'databaseVersions': [1, 1],
        'scope': 'actual dev.8 APK to dev.9 APK, reading/bookmarks/sessions/attempts/mistakes/preferences',
        'notCovered': ['Room schema migration (no schema change)', 'release signing key', 'physical device']}, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__': main()
