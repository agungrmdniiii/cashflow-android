import json
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

if __name__ == "__main__":
    unittest.main()
