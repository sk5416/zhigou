"""Background am-kill exercises saved activity state; it does not simulate memory pressure."""
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from run_process_recovery import adb, PACKAGE, RUNNER

def phase(name):
    output = adb('shell', 'am', 'instrument', '-w', '-e', 'class', PACKAGE + '.UpgradeTest',
                 '-e', 'upgradePhase', name, '-e', 'expectedVersion', '9', RUNNER)
    if 'OK (1 test)' not in output: raise RuntimeError(output)

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/zhigou-window.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/zhigou-window.xml'))

def wait_text(text):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        root = tree()
        node = next((n for n in root.iter('node') if n.get('text') == text), None)
        if node is not None: return node, root
        time.sleep(.5)
    raise RuntimeError(f'UI text missing: {text}')

def main():
    phase('seed')
    adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', '-n', PACKAGE + '/.MainActivity')
    wait_text('把知识，构成判断力。')
    # Practice follows the main tiles on a small screen.
    node = None
    for _ in range(5):
        root = tree(); node = next((n for n in root.iter('node') if n.get('text') == '继续章节练习'), None)
        if node is not None: break
        adb('shell', 'input', 'swipe', '160', '490', '160', '240', '350')
    if node is None: raise RuntimeError('Unfinished practice is unreachable')
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    _, before = wait_text('章节练习 · 2/8')
    dest = Path('device-screenshots'); dest.mkdir(exist_ok=True)
    (dest/'background-before.xml').write_bytes(ET.tostring(before, encoding='utf-8'))
    (dest/'background-before.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p']))
    if not any(n.get('checked') == 'true' for n in before.iter('node')): raise RuntimeError('Seeded selection not visible')
    old = adb('shell', 'pidof', PACKAGE)
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(1)
    adb('shell', 'am', 'kill', PACKAGE)
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline and adb('shell', 'pidof', PACKAGE, check=False): time.sleep(.25)
    if adb('shell', 'pidof', PACKAGE, check=False): raise RuntimeError('Background process was not killed')
    adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', '-n', PACKAGE + '/.MainActivity')
    _, after = wait_text('章节练习 · 2/8')
    (dest/'background-after.xml').write_bytes(ET.tostring(after, encoding='utf-8'))
    if not any(n.get('checked') == 'true' for n in after.iter('node')): raise RuntimeError('Selection missing after process recreation')
    new = adb('shell', 'pidof', PACKAGE)
    if old == new or not new.isdecimal(): raise RuntimeError('No evidence of a new app process')
    (dest/'background-after.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p']))
    phase('verify')
    path = Path('.artifacts/background-recovery-report.json'); path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps({'status':'passed','stoppedPid':int(old),'verifiedPid':int(new),'scope':'Home -> am kill background process -> launcher, quiz page 2/8 and selection restored; stored data verified',
        'notCovered':['real low-memory pressure policy','arbitrary nested navigation stacks','physical device']},indent=2)+'\n',encoding='utf-8')

if __name__ == '__main__': main()
