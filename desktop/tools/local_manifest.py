"""Bind operator-selected trusted local JARs to their metadata and exact bytes.

This does not authenticate a downloaded JAR. Only use artifacts built from a
trusted checkout, or verified by a separate trusted delivery channel.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def attributes(jar: Path) -> dict[str, str]:
    with zipfile.ZipFile(jar) as archive:
        text = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    unfolded = text.replace("\r\n ", "").replace("\n ", "")
    return dict(line.split(": ", 1) for line in unfolded.splitlines() if ": " in line)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--library", type=Path)
    args = parser.parse_args()
    jar = args.jar.resolve(strict=True)
    manifest = attributes(jar)
    fields = {
        "source.file": str(jar),
        "source.sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
    }
    for key, attribute in {
        "package": "Source-Package", "class": "Source-Class", "versionCode": "Version-Code",
        "versionName": "Version-Name", "sourceVersionCode": "Source-Version-Code", "coreApi": "Core-Api",
    }.items():
        fields[f"source.{key}"] = manifest[f"Grimoire-{attribute}"]
    if args.library:
        library = args.library.resolve(strict=True)
        coordinates = [value.strip() for value in manifest["Grimoire-Host-Libraries"].split(",")
                       if value.strip().startswith("io.grimoire:extensions-lib:")]
        if len(coordinates) != 1:
            raise ValueError("Expected exactly one extensions-lib requirement")
        fields.update({"library.file": str(library), "library.sha256": hashlib.sha256(library.read_bytes()).hexdigest(),
                       "library.coordinate": coordinates[0]})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Java Properties escaping, including non-ASCII Windows paths and backslashes.
    body = "# Trusted local artifacts; not a remote extension index\n" + "".join(
        key + "=" + json.dumps(value, ensure_ascii=True)[1:-1] + "\n" for key, value in fields.items())
    args.output.write_text(body, encoding="ascii", newline="\n")
    print(f"Wrote {args.output}; source SHA-256 {fields['source.sha256']}")


if __name__ == "__main__":
    main()
