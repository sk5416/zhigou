"""Download pinned development tools into ignored .tools; never modifies user SDKs."""
from pathlib import Path
import concurrent.futures
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / '.tools'
DEST.mkdir(exist_ok=True)

def get(name, url, target):
    archive = DEST / name
    if not archive.exists():
        print('Downloading', name, flush=True)
        urllib.request.urlretrieve(url, archive)
    if not target.exists():
        print('Extracting', name, flush=True)
        with zipfile.ZipFile(archive) as z:
            z.extractall(target)
    return str(target)

with concurrent.futures.ThreadPoolExecutor() as pool:
    tasks = [
        pool.submit(get, 'gradle.zip', 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip', DEST / 'gradle'),
        pool.submit(get, 'cmdline.zip', 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip', DEST / 'android-cli'),
    ]
    for task in tasks:
        print(task.result(), flush=True)
