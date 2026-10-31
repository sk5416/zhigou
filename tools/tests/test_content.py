import copy
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
        self.assertEqual(len(c['questions']), 24)
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

if __name__ == '__main__':
    unittest.main()
