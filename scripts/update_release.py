"""Generate the public update feed from the APK actually built. Never copies APKs."""
import argparse
import hashlib
import json
import re
from pathlib import Path

REPOSITORY = "agungrmdniiii/cashflow-android"

def generate(apk, build_metadata, notes_file, tag, output_dir):
    build = json.loads(Path(build_metadata).read_text(encoding="utf-8"))["elements"]
    if len(build) != 1:
        raise ValueError("Publish one universal APK, not split APKs")
    artifact = build[0]
    name, code = artifact["versionName"], artifact["versionCode"]
    if not isinstance(code, int) or code <= 110 or not re.fullmatch(r"[0-9]+[.][0-9]+[.][0-9]+", name):
        raise ValueError("Invalid version; preserve an internal versionCode above existing tester builds")
    if tag != f"v{name}-build.{code}":
        raise ValueError("Release tag and APK version must match")
    notes = Path(notes_file).read_text(encoding="utf-8").strip()
    lines = notes.splitlines()
    if not 1 <= len(lines) <= 20 or len(notes) > 12000 or any(not line.startswith("- ") or len(line) < 12 for line in lines):
        raise ValueError("Write concise Indonesian release notes as non-empty bullet points")
    apk = Path(apk)
    if apk.name != artifact["outputFile"] or not 0 < apk.stat().st_size <= 200 * 1024 * 1024:
        raise ValueError("APK does not match build output metadata")
    digest = hashlib.sha256()
    with apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            digest.update(chunk)
    metadata = {"versionCode": code, "versionName": name, "minSdk": 26,
        "size": apk.stat().st_size, "sha256": digest.hexdigest(),
        "apkUrl": f"https://github.com/{REPOSITORY}/releases/download/{tag}/DuitAing.apk", "notes": notes}
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "update.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output_dir / "release-notes.txt").write_text(notes + "\n", encoding="utf-8")
    return metadata

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["apk", "build-metadata", "notes-file", "tag", "output-dir"]:
        parser.add_argument("--" + name, required=True)
    args = parser.parse_args()
    result = generate(args.apk, args.build_metadata, args.notes_file, args.tag, args.output_dir)
    print(f"Prepared Duit Aing {result['versionName']} build {result['versionCode']}; APK unchanged")
