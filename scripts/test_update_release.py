import json
import io
import os
import runpy
from unittest.mock import patch
import tempfile
import unittest
from pathlib import Path
from update_release import generate

class UpdateReleaseTest(unittest.TestCase):
    def test_generates_matching_feed_without_copying_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "app-debug.apk"
            apk.write_bytes(b"small synthetic test fixture")
            build = root / "output-metadata.json"
            build.write_text(json.dumps({"elements": [{"versionName": "1.0.0", "versionCode": 111, "outputFile": apk.name}]}))
            notes = root / "notes.txt"
            notes.write_text("- Memperbaiki pencatatan suara.", encoding="utf-8")
            result = generate(apk, build, notes, "v1.0.0-build.111", root / "feed")
            self.assertEqual(111, result["versionCode"])
            self.assertEqual(apk.stat().st_size, result["size"])
            self.assertEqual(64, len(result["sha256"]))
            self.assertFalse(list((root / "feed").glob("*.apk")))
            with self.assertRaises(ValueError):
                generate(apk, build, notes, "v1.0.1-build.111", root / "feed")
            notes.write_text("", encoding="utf-8")
            with self.assertRaises(ValueError):
                generate(apk, build, notes, "v1.0.0-build.111", root / "feed")


    def release_code(self, previous_code):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output.txt"
            response = io.BytesIO(json.dumps({"versionCode": previous_code, "notes": "Previous release"}).encode())
            with patch.dict(os.environ, {"GITHUB_RUN_NUMBER": "999", "GITHUB_OUTPUT": str(output)}), patch("urllib.request.urlopen", return_value=response):
                runpy.run_path(str(Path(__file__).with_name("next_update_version.py")), run_name="__main__")
            return int(dict(line.split("=", 1) for line in output.read_text().splitlines())["code"])

    def test_release_uses_source_floor_without_adding_workflow_run_number(self):
        properties = dict(line.split("=", 1) for line in (Path(__file__).resolve().parent.parent / "version.properties").read_text().splitlines() if line and not line.startswith("#"))
        floor = int(properties["versionCode"])
        self.assertEqual(floor, self.release_code(floor - 1))

    def test_release_advances_beyond_newer_published_version(self):
        self.assertEqual(2001, self.release_code(2000))

    def test_release_rejects_android_version_code_overflow(self):
        with self.assertRaises(ValueError):
            self.release_code(2147483647)

if __name__ == "__main__":
    unittest.main()
