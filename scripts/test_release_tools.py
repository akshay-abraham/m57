# SPDX-License-Identifier: AGPL-3.0-only
import runpy
import tempfile
import unittest
from pathlib import Path

prepare = runpy.run_path(str(Path(__file__).with_name('prepare-release.py')))['prepare']

class ReleasePreparationTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'app').mkdir()
        (self.root / 'docs/releases').mkdir(parents=True)
        self.build = self.root / 'app/build.gradle.kts'
        self.original = 'versionCode = 8\nversionName = "0.6.2"\n'
        self.build.write_text(self.original)

    def test_new_version_increments_android_code_and_preserves_notes(self):
        self.assertEqual(prepare(self.root, '0.7.0', 'Keep all ideas.\nFix DNS.'), 9)
        self.assertIn('versionCode = 9', self.build.read_text())
        self.assertIn('versionName = "0.7.0"', self.build.read_text())
        self.assertIn('Keep all ideas.\nFix DNS.', (self.root / 'docs/releases/0.7.0.md').read_text())

    def test_invalid_or_older_versions_do_not_modify_source(self):
        for version in ('0.6.2', '0.5.9', 'v0.7.0', '0.7.0; echo unsafe', '../0.7.0', '00.7.0'):
            with self.subTest(version=version), self.assertRaises(ValueError):
                prepare(self.root, version, '')
            self.assertEqual(self.build.read_text(), self.original)

    def test_existing_notes_are_not_overwritten(self):
        notes = self.root / 'docs/releases/0.7.0.md'
        notes.write_text('Previously written ideas')
        with self.assertRaises(ValueError):
            prepare(self.root, '0.7.0', 'Replacement')
        self.assertEqual(notes.read_text(), 'Previously written ideas')
        self.assertEqual(self.build.read_text(), self.original)

if __name__ == '__main__':
    unittest.main()
