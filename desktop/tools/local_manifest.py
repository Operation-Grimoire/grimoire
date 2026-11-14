"""Bind operator-selected trusted local JARs to their metadata and exact bytes.

This does not authenticate a downloaded JAR. Only use artifacts built from a
trusted checkout, or verified by a separate trusted delivery channel.
"""
import argparse
import hashlib
import io
from pathlib import Path
import zipfile


def attributes(data: bytes) -> dict[str, str]:
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        text = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    unfolded = text.replace("\r\n ", "").replace("\n ", "")
    main_section = unfolded.replace("\r\n", "\n").split("\n\n", 1)[0]
    return dict(line.split(": ", 1) for line in main_section.splitlines() if ": " in line)


def properties_value(value: str) -> str:
    units = value.encode("utf-16-be", errors="surrogatepass")
    result = []
    for index in range(0, len(units), 2):
        unit = int.from_bytes(units[index:index + 2], "big")
        if 32 <= unit <= 126:
            char = chr(unit)
            result.append(("\\" if char in "\\=:#! " else "") + char)
        else:
            result.append(f"\\u{unit:04x}")
    return "".join(result)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--library", type=Path)
    args = parser.parse_args()
    jar = args.jar.resolve(strict=True)
    source_bytes = jar.read_bytes()
    manifest = attributes(source_bytes)
    fields = {
        "source.file": str(jar),
        "source.sha256": hashlib.sha256(source_bytes).hexdigest(),
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
        key + "=" + properties_value(value) + "\n" for key, value in fields.items())
    args.output.write_text(body, encoding="ascii", newline="\n")
    print(f"Wrote {args.output}; source SHA-256 {fields['source.sha256']}")


if __name__ == "__main__":
    main()
