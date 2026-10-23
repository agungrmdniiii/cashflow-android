"""Choose a monotonically increasing APK version for the serialized release workflow."""
import json
import os
import urllib.error
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parent.parent
properties = dict(line.split("=", 1) for line in (root / "version.properties").read_text().splitlines() if line and not line.startswith("#"))
base = int(properties["versionCode"])
name = properties["versionName"]
notes = (root / "app/src/main/res/raw/release_notes.txt").read_text(encoding="utf-8").strip()
previous = {}
request = urllib.request.Request("https://github.com/agungrmdniiii/cashflow-android/releases/latest/download/update.json", headers={"User-Agent": "DuitAing-release-workflow"})
try:
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read(65537)
        if len(data) > 65536:
            raise ValueError("Published metadata is oversized")
        previous = json.loads(data)
except urllib.error.HTTPError as error:
    if error.code != 404:
        raise
if previous.get("notes", "").strip() == notes:
    raise ValueError("Perbarui release_notes.txt agar catatan sesuai dengan revisi yang akan diterbitkan.")
code = max(base, int(previous.get("versionCode", 0)) + 1)
if not 110 < code <= 2147483647:
    raise ValueError("Invalid internal version code")
with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
    output.write(f"code={code}\nname={name}\ntag=v{name}-build.{code}\n")
print(f"Duit Aing {name}, build {code}")
