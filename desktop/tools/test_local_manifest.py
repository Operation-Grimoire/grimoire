import base64
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

from local_manifest import attributes, properties_value


class LocalManifestTest(unittest.TestCase):
    def test_java_properties_round_trip_paths_spaces_controls_and_surrogate_pairs(self):
        values = [" leading space", "C:\\path with spaces\\book.jar", "quotes\" =:#!", "\b\t\n\r\x1f", "\u4e66 \U0001f4d6"]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            props = root / "test.properties"
            props.write_text("".join(f"key{i}={properties_value(value)}\n" for i, value in enumerate(values)), encoding="ascii")
            java = root / "ReadProperties.java"
            java.write_text('''
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
class ReadProperties {
  public static void main(String[] args) throws Exception {
    Properties p = new Properties();
    try (var input = Files.newInputStream(Path.of(args[0]))) { p.load(input); }
    for (int i = 0; i < p.size(); i++) {
      System.out.println(Base64.getEncoder().encodeToString(p.getProperty("key" + i).getBytes(StandardCharsets.UTF_8)));
    }
  }
}
''', encoding="utf-8")
            result = subprocess.run(["java", str(java), str(props)], check=True, capture_output=True)
            self.assertEqual(values, [base64.b64decode(line).decode("utf-8") for line in result.stdout.splitlines()])

    def test_manifest_unfolds_only_main_attributes(self):
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, "w") as jar:
            jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nGrimoire-Source-Package: io.grimoire.\r\n extension.real\r\n\r\nName: entry.class\r\nGrimoire-Source-Package: wrong\r\n")
        self.assertEqual("io.grimoire.extension.real", attributes(archive.getvalue())["Grimoire-Source-Package"])


if __name__ == "__main__":
    unittest.main()
