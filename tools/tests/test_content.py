import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('content', ROOT / 'tools/content.py')
content = importlib.util.module_from_spec(spec)
spec.loader.exec_module(content)

class ContentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name) / 'content'
        shutil.copytree(ROOT / 'content', self.directory)
        (self.directory.parent / 'docs').mkdir()
        shutil.copy2(ROOT / 'docs/05_课程审核记录.md', self.directory.parent / 'docs/05_课程审核记录.md')
    def tearDown(self):
        self.temp.cleanup()
    def change(self, relative, mutate):
        file = self.directory / relative
        data = json.loads(file.read_text(encoding='utf-8'))
        mutate(data)
        file.write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')
    def test_sample_complete_structure(self):
        c = content.load_and_validate(self.directory)
        self.assertEqual(len(c['chapters']), 81)
        published = {ch['id'] for ch in c['chapters'] if ch['published'] and ch['id'].startswith('C')}
        self.assertTrue({'C00', 'C02', 'C12'} <= published)
        self.assertEqual({q['chapterId'] for q in c['questions']}, published)
        self.assertIn('C00-Q01', {q['id'] for q in c['questions']})
    def test_release_gate_rejects_partial_course(self):
        with self.assertRaisesRegex(ValueError, '未交付'):
            content.load_and_validate(self.directory, release=True)
    def test_cycle_rejected(self):
        self.change('catalog.json', lambda data: data['chapters'][0].update(prerequisites=['C02']))
        with self.assertRaisesRegex(ValueError, '有环'):
            content.load_and_validate(self.directory)
    def test_duplicate_option_rejected(self):
        self.change('questions/C00.json', lambda data: data[0]['options'][1].update(id='A'))
        with self.assertRaisesRegex(ValueError, '选项'):
            content.load_and_validate(self.directory)
    def test_missing_explanation_rejected(self):
        self.change('questions/C12.json', lambda data: data[0]['options'][0].update(explanation=''))
        with self.assertRaisesRegex(ValueError, '解析'):
            content.load_and_validate(self.directory)
    def test_duplicate_stem_rejected(self):
        self.change('questions/C00.json', lambda data: data[1].update(stem=data[0]['stem']))
        with self.assertRaisesRegex(ValueError, '题干重复'):
            content.load_and_validate(self.directory)
    def test_duplicate_option_text_rejected(self):
        self.change('questions/C00.json', lambda data: data[0]['options'][1].update(text=data[0]['options'][0]['text']))
        with self.assertRaisesRegex(ValueError, '文字重复'):
            content.load_and_validate(self.directory)
    def test_boolean_revision_rejected(self):
        self.change('questions/C00.json', lambda data: data[0].update(revision=True))
        with self.assertRaisesRegex(ValueError, '修订号'):
            content.load_and_validate(self.directory)
    def test_current_reviews_match_artifacts(self):
        content.load_and_validate(self.directory, reviewed=True)
    def test_review_invalidated_by_markdown_change(self):
        f = self.directory / 'chapters/C00.md'
        f.write_text(f.read_text(encoding='utf-8') + '\n\n新增一句。', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '审核已过期'):
            content.load_and_validate(self.directory, reviewed=True)
    def test_review_rejects_corrupted_text(self):
        self.change('reviews.json', lambda data: data['C00'].update(note='????????'))
        with self.assertRaisesRegex(ValueError, '文字损坏'):
            content.load_and_validate(self.directory, reviewed=True)
    def test_review_rejects_missing_record(self):
        self.change('reviews.json', lambda data: data['C00'].update(record='docs/missing.md'))
        with self.assertRaisesRegex(ValueError, '审核文档不存在'):
            content.load_and_validate(self.directory, reviewed=True)
    def test_review_record_cannot_escape_docs(self):
        self.change('reviews.json', lambda data: data['C00'].update(record='content/catalog.json'))
        with self.assertRaisesRegex(ValueError, '超出docs'):
            content.load_and_validate(self.directory, reviewed=True)
    def test_review_invalidated_by_answer_change(self):
        self.change('questions/C00.json', lambda data: data[0].update(answers=['A']))
        with self.assertRaisesRegex(ValueError, '审核已过期'):
            content.load_and_validate(self.directory, reviewed=True)
    def test_review_invalidated_by_metadata_change(self):
        self.change('catalog.json', lambda data: data['chapters'][0].update(title='修改章名'))
        with self.assertRaisesRegex(ValueError, '审核已过期'):
            content.load_and_validate(self.directory, reviewed=True)
    def test_review_invalidated_by_source_change(self):
        self.change('sources.json', lambda data: data['sources'][0].update(claims='变更说明'))
        with self.assertRaisesRegex(ValueError, '审核已过期'):
            content.load_and_validate(self.directory, reviewed=True)
    def test_remote_image_rejected(self):
        file = self.directory / 'chapters/C02.md'
        file.write_text(file.read_text(encoding='utf-8') + '\n![remote](https://example.com/x.png)', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '远程图片'):
            content.load_and_validate(self.directory)
    def test_local_resource_cannot_escape_content_directory(self):
        file = self.directory / 'chapters/C02.md'
        file.write_text(file.read_text(encoding='utf-8') + '\n![escape](../../outside.png)', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '超出内容目录'):
            content.load_and_validate(self.directory)
    def test_render_has_anchors_tables_and_csp(self):
        result = content.render('# A\n\n## B\n\n| x | y |\n|---|---|\n| 1 | 2 |')
        self.assertIn('id="s1"', result)
        self.assertIn('<table>', result)
        self.assertIn('Content-Security-Policy', result)
        self.assertIn('<details><summary>本章导航</summary>', result)
    def test_code_comments_are_not_headings_or_modified(self):
        source = '# Title\n\n```python\n# keep this comment\nvalue = 3\n```\n\n## Case\nbody'
        self.assertEqual(content.heading_records(source), [{'anchor': 's0', 'line': 0}, {'anchor': 's1', 'line': 7}])
        rendered = content.render(source)
        self.assertIn('# keep this comment\nvalue = 3', rendered)
        self.assertNotIn('comment {#', rendered)
        self.assertIn('id="s1"', rendered)
        self.assertNotIn('id="s2"', rendered)
    def test_tilde_fence_and_heading_like_string_stay_code(self):
        source = '# A\n\n~~~text\n## not a heading\n~~~\n\n## B'
        self.assertEqual([h['line'] for h in content.heading_records(source)], [0, 6])
        self.assertIn('## not a heading', content.render(source))
    def test_teaching_heading_inside_code_does_not_satisfy_requirement(self):
        f = self.directory / 'chapters/C00.md'
        f.write_text(f.read_text(encoding='utf-8').replace('## PM 评审问题', '```text\n## PM 评审问题\n```'), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '教学要素'):
            content.load_and_validate(self.directory)

if __name__ == '__main__':
    unittest.main()
