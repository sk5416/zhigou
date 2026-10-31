"""Fail before boot if outdated avdmanager silently wrote android-0 for a dotted SDK."""
import json
import os
import sys
from pathlib import Path

directory = Path(os.environ.get('ANDROID_AVD_HOME', Path.home()/'.android/avd'))
values = dict(line.split('=', 1) for line in (directory/'test.ini').read_text().splitlines() if '=' in line)
expected = 'android-' + sys.argv[1]
actual = values.get('target')
path = Path('.artifacts/avd-environment.json'); path.parent.mkdir(exist_ok=True)
path.write_text(json.dumps({'expectedTarget':expected,'actualTarget':actual,'valid':actual==expected},indent=2)+'\n',encoding='utf-8')
if actual != expected:
    raise RuntimeError(f'AVD metadata mismatch: expected {expected}, got {actual}; update avdmanager before testing')
print('AVD metadata verified:', actual, flush=True)
