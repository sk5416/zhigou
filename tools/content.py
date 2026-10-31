"""Validate authored curriculum and compile only published lessons into offline assets."""
from pathlib import Path
import argparse
import json
import re
import hashlib
import shutil
from urllib.parse import urlsplit
import markdown
from markdown.extensions.fenced_code import FencedBlockPreprocessor
from markdown.blockprocessors import HashHeaderProcessor

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / 'content'
HEADINGS = ['结论先行', '业务场景', '核心概念', '工作原理', '企业案例', 'AI 易忽视', 'PM 评审', '微实践', '小结']

def heading_records(body):
    # Use the same pinned Markdown parser's fence/header grammar as rendering.
    # Preserve line numbers while masking code so '# comments' stay code.
    masked = FencedBlockPreprocessor.FENCED_BLOCK_RE.sub(lambda m: '\n' * m.group(0).count('\n'), body)
    records = []
    for match in HashHeaderProcessor.RE.finditer(masked):
        line = masked[:match.start('level')].count('\n')
        records.append({'anchor': f's{len(records)}', 'line': line})
    return records

def review_fingerprint(directory, chapter):
    cid = chapter['id']
    questions_file = directory / 'questions' / f'{cid}.json'
    sources_file = directory / 'sources.json'
    sources = json.loads(sources_file.read_text(encoding='utf-8')).get('sources', []) if sources_file.exists() else []
    payload = {
        'metadata': chapter,
        'markdown': (directory / 'chapters' / f'{cid}.md').read_text(encoding='utf-8'),
        'questions': json.loads(questions_file.read_text(encoding='utf-8')) if cid.startswith('C') else [],
        'sources': [s for s in sources if cid in s.get('chapters', [])],
    }
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')).hexdigest()

def verify_reviews(directory, chapters):
    audits = json.loads((directory / 'reviews.json').read_text(encoding='utf-8'))
    for c in chapters:
        audit = audits.get(c['id'], {})
        fields = ('reviewer', 'method', 'reviewedAt', 'record', 'note')
        if audit.get('status') != 'reviewed' or any(not isinstance(audit.get(k), str) or not audit[k].strip() for k in fields):
            raise ValueError(f"{c['id']} 尚无完整审核记录")
        if any(re.search(r'\?{2,}|\ufffd', audit[k]) for k in fields):
            raise ValueError(f"{c['id']} 审核记录文字损坏")
        record = (directory.parent / audit['record']).resolve()
        if not record.is_relative_to((directory.parent / 'docs').resolve()) or not record.is_file():
            raise ValueError(f"{c['id']} 审核文档不存在或超出docs目录")
        if audit.get('fingerprint') != review_fingerprint(directory, c):
            raise ValueError(f"{c['id']} 审核已过期，正文、题库、元数据或来源已变更")

def load_and_validate(directory=CONTENT, release=False, reviewed=False):
    meta = json.loads((directory / 'catalog.json').read_text(encoding='utf-8'))
    ids = [c['id'] for c in meta['chapters']]
    if len(ids) != len(set(ids)):
        raise ValueError('章节编号重复')
    if set(ids) != {f'C{i:02d}' for i in range(73)} | {f'A{x}' for x in 'ABCDEFGH'}:
        raise ValueError('课程目录须保留全部 73 章与八附录')
    visited, visiting = set(), set()
    index = {c['id']: c for c in meta['chapters']}
    def visit(cid):
        if cid in visiting:
            raise ValueError('先修关系有环')
        if cid in visited:
            return
        visiting.add(cid)
        for p in index[cid].get('prerequisites', []):
            if p not in index:
                raise ValueError('不存在的先修章节')
            visit(p)
        visiting.remove(cid)
        visited.add(cid)
    for c in meta['chapters']:
        visit(c['id'])
        if any(r not in index for r in c.get('related', [])):
            raise ValueError('关联章节不存在')
    questions, chapters = [], []
    for c in meta['chapters']:
        c = dict(c)
        c['body'], c['html'] = '', ''
        if c['published']:
            source = directory / 'chapters' / f"{c['id']}.md"
            c['body'] = source.read_text(encoding='utf-8')
            c['html'] = f"chapters/{c['id']}.html"
            if c['id'].startswith('C'):
                heading_text = '\n'.join(c['body'].splitlines()[h['line']] for h in heading_records(c['body']))
                if any(not re.search(r'^## .*' + re.escape(h), heading_text, re.M) for h in HEADINGS):
                    raise ValueError(f"{c['id']} 缺少教学要素")
                qs = json.loads((directory / 'questions' / f"{c['id']}.json").read_text(encoding='utf-8'))
                if not 8 <= len(qs) <= 12:
                    raise ValueError(f"{c['id']} 题数须为 8～12")
                stems = set()
                for q in qs:
                    options = q['options']
                    option_ids = [o['id'] for o in options]
                    if q['chapterId'] != c['id'] or not re.fullmatch(c['id'] + r'-Q\d{2,}', q['id']) or type(q['revision']) is not int or q['revision'] < 1:
                        raise ValueError('题号、所属章节或修订号错误')
                    normalized_stem = re.sub(r'\s+', '', q['stem'])
                    if normalized_stem in stems:
                        raise ValueError('同章题干重复')
                    stems.add(normalized_stem)
                    if q['ability'] not in ('概念', '判断', '场景') or q['difficulty'] not in ('基础', '应用', '进阶'):
                        raise ValueError('题目能力或难度标记无效')
                    if q['type'] not in ('single', 'multiple') or len(option_ids) != len(set(option_ids)) or len(option_ids) < 2:
                        raise ValueError('题型或选项错误')
                    if not q['answers'] or len(q['answers']) != len(set(q['answers'])) or not set(q['answers']) <= set(option_ids):
                        raise ValueError('正确答案错误')
                    if (q['type'] == 'single' and len(q['answers']) != 1) or (q['type'] == 'multiple' and len(q['answers']) < 2):
                        raise ValueError('题型与答案数量不符')
                    if any(not o['text'].strip() or not o['explanation'].strip() for o in options):
                        raise ValueError('缺少选项文字或解析')
                    if len({re.sub(r'\s+', '', o['text']) for o in options}) != len(options):
                        raise ValueError('选项文字重复')
                    if not q['knowledge'] or any(k not in c['knowledge'] for k in q['knowledge']):
                        raise ValueError('知识点引用错误')
                    if any(not q[k].strip() for k in ('stem', 'difficulty', 'ability', 'misconception')):
                        raise ValueError('缺少题目教学字段')
                questions.extend(qs)
            for target in re.findall(r'!?\[[^\]]*\]\(([^ )]+)', c['body']):
                parsed = urlsplit(target)
                if parsed.scheme == 'zhigou':
                    if parsed.path.strip('/') not in index:
                        raise ValueError('章内链接不存在')
                elif not target.startswith('#') and not parsed.scheme:
                    resource = (source.parent / parsed.path).resolve()
                    if not resource.is_relative_to(directory.resolve()) or not resource.is_file():
                        raise ValueError('本地资源不存在或超出内容目录')
                elif parsed.scheme not in ('', 'zhigou', 'https'):
                    raise ValueError('不允许的资源协议')
            if re.search(r'!\[[^\]]*\]\(https?://', c['body']) or re.search(r'<\s*(script|iframe|img|object|style)\b', c['body'], re.I):
                raise ValueError('不允许远程图片或主动 HTML 内容')
        # Only runtime-facing fields are emitted; source-only knowledge/review fields stay in source.
        runtime = {k: c[k] for k in ('id', 'title', 'part', 'depth', 'priority', 'prerequisites', 'related', 'body', 'html', 'published')}
        runtime['headings'] = heading_records(c['body'])
        chapters.append(runtime)
    qids = [q['id'] for q in questions]
    if len(qids) != len(set(qids)):
        raise ValueError('题号重复')
    if release:
        if not all(c['published'] for c in chapters):
            raise ValueError('正式 MVP 不允许未交付章节或附录')
    if release or reviewed:
        verify_reviews(directory, [c for c in meta['chapters'] if c['published']])
    return {'version': meta['version'], 'chapters': chapters, 'questions': questions}

CSS = '''body{font:18px/1.85 sans-serif;max-width:48em;margin:auto;padding:18px;color:#253b32;background:#f7f8f3;overflow-wrap:anywhere}h1{font-size:1.65em}h2{font-size:1.3em;margin-top:2em}a{color:#246b5b}pre,blockquote{background:#e8eee7;padding:12px;border-radius:8px}pre{overflow-x:auto}code{font-size:.9em}table{border-collapse:collapse;display:block;overflow:auto}th,td{border:1px solid #b5c8ba;padding:8px}img{max-width:100%}.toc{font-size:.9em}'''
def render(body):
    headings = {h['line']: h['anchor'] for h in heading_records(body)}
    lines = []
    for i, line in enumerate(body.splitlines()):
        if i in headings:
            line += ' {#' + headings[i] + '}'
        lines.append(line)
    result = markdown.markdown('[TOC]\n\n' + '\n'.join(lines), extensions=['tables', 'fenced_code', 'toc', 'attr_list'])
    result = re.sub(r'<div class="toc">.*?</div>', lambda match: '<details><summary>本章导航</summary>' + match.group(0) + '</details>', result, count=1, flags=re.S)
    return f'<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src https://appassets.androidplatform.net; style-src \'unsafe-inline\'"><style>{CSS}</style></head><body>{result}</body></html>'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['check', 'build'])
    parser.add_argument('--release', action='store_true')
    parser.add_argument('--reviewed', action='store_true', help='Verify recorded reviews match the current published artifacts, not their teaching correctness')
    args = parser.parse_args()
    catalog = load_and_validate(release=args.release, reviewed=args.reviewed)
    if args.command == 'build':
        dest = ROOT / 'app/build/generated/courseAssets'
        (dest / 'chapters').mkdir(parents=True, exist_ok=True)
        if (CONTENT / 'assets').exists():
            shutil.copytree(CONTENT / 'assets', dest / 'assets', dirs_exist_ok=True)
        for c in catalog['chapters']:
            if c['published']:
                (dest / c['html']).write_text(render(c['body']), encoding='utf-8')
        (dest / 'catalog.json').write_text(json.dumps(catalog, ensure_ascii=False), encoding='utf-8')
    print(f"Content OK: {sum(c['published'] for c in catalog['chapters'])}/81 published, {len(catalog['questions'])} questions. Structure validation is not editorial approval.")

if __name__ == '__main__':
    main()
