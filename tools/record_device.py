"""Record observed runtime identity without personal device or account identifiers."""
import json
from pathlib import Path
from run_process_recovery import adb

report = {key: adb('shell', 'getprop', prop) for key, prop in {
    'release': 'ro.build.version.release', 'sdk': 'ro.build.version.sdk',
    'codename': 'ro.build.version.codename', 'buildId': 'ro.build.id',
    'securityPatch': 'ro.build.version.security_patch', 'abi': 'ro.product.cpu.abi',
}.items()}
report['pageSize'] = adb('shell', 'getconf', 'PAGESIZE')
path = Path('.artifacts/device-environment.json'); path.parent.mkdir(exist_ok=True)
path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print('Observed emulator runtime:', json.dumps(report), flush=True)
