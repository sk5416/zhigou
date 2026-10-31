"""One-time catalog extraction from the immutable requirements; never fabricates lessons."""
import json
import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
target = root / 'content/catalog.json'
if target.exists():
    raise SystemExit('Catalog already exists; refusing to overwrite')
source = (root / 'docs/02_完整知识体系与课程规范.md').read_text(encoding='utf-8')
chapters = []
part = ''
for line in source.splitlines():
    if re.match(r'## 第\d+篇', line):
        part = line.removeprefix('## ')
    m = re.match(r'- \*\*(C\d\d)｜(.+?)\*\* `\[(L\d)·(P\d)\]`', line)
    if m:
        cid, title, depth, priority = m.groups()
        chapters.append(dict(id=cid, title=title, part=part, depth=depth, priority=priority, prerequisites=[], related=[], knowledge=[f'{cid}-K{i:02d}' for i in range(1,9)], published=cid in ['C00','C02','C12']))
for code, title in re.findall(r'- \*\*附录 ([A-H])｜([^*]+)\*\*', source):
    chapters.append(dict(id='A'+code, title=title, part='附录｜随手查阅', depth='', priority='', prerequisites=[], related=[], knowledge=[], published=False))
dependencies = {'C02':['C00'], 'C10':['C02'], 'C11':['C02'], 'C12':['C11'], 'C13':['C11'], 'C14':['C13'], 'C43':['C11','C12']}
for c in chapters:
    c['prerequisites'] = dependencies.get(c['id'], [])
    c['related'] = {'C00':['C02','C12'], 'C02':['C00','C12'], 'C12':['C02','C43']}.get(c['id'], [])
target.parent.mkdir(exist_ok=True)
target.write_text(json.dumps(dict(version='2026.10-sample.1', chapters=chapters), ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
