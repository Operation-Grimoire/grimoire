import os
import hashlib
import json
from pathlib import Path
import subprocess
import sqlite3
import chapter_codec_beta as beta
import release_policy as policy
import sys
import tempfile
import unittest


class SignedBetaHarnessGuardTest(unittest.TestCase):
    def run_guard(self, optimized, ci_like=False):
        script = Path(__file__).with_name("chapter_codec_beta.py").resolve()
        env = dict(os.environ, PATH="", GITHUB_ACTIONS="true" if optimized or ci_like else "false",
                   RUNNER_OS="Linux", CODEC_BETA_TAG="v1.0.5-beta.343", GITHUB_SHA="0" * 40)
        with tempfile.TemporaryDirectory() as directory:
            env.update(RUNNER_ENVIRONMENT="github-hosted", GITHUB_WORKSPACE=directory)
            command = [sys.executable, *(["-O"] if optimized else []), str(script)]
            result = subprocess.run(command, cwd=directory, env=env, capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("CI-only fixture harness", result.stderr)
            if optimized:
                self.assertIn("Python assertions must be enabled", result.stderr)
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_workstation_execution_stops_before_files_or_device_access(self):
        self.run_guard(False)

    def test_optimized_python_cannot_bypass_fixture_guards(self):
        self.run_guard(True)

    def test_ci_looking_environment_cannot_enable_a_workstation_directory(self):
        self.run_guard(False, ci_like=True)


class FrozenBaselineTest(unittest.TestCase):
    def test_frozen_schema_matches_the_pinned_db33_baseline_without_user_rows(self):
        self.assertEqual("v1.0.5-beta.347", beta.BASELINE)
        self.assertIn(beta.BASELINE, policy.PINNED_ANDROID_BETAS)
        self.assertEqual("ac5569f882663ec3aa8e653f1b0cf61384b91bb103dead8a564511342727ef71", beta.BASELINE_SHA256)
        schema = Path(__file__).resolve().parents[2] / "app/src/androidTest/assets/chapter-codec/v33.sql"
        with sqlite3.connect(":memory:") as db:
            db.executescript(schema.read_text(encoding="utf-8"))
            self.assertEqual(33, db.execute("PRAGMA user_version").fetchone()[0])
            self.assertEqual([("af03604692f0d6032635caa46a5b4683",)], db.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchall())
            # Independent complete oracle from signed beta347 run43687721590's verified DB33 snapshot,
            # not generated from v33.sql. Exclude SQLite/Android-owned implementation tables only.
            objects = db.execute("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE sql IS NOT NULL "
                                 "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY type,name").fetchall()
            self.assertEqual(22, len(objects))
            digest = hashlib.sha256(json.dumps(objects, separators=(",", ":"), ensure_ascii=True).encode()).hexdigest()
            self.assertEqual("4c6fc65baef38fab434d5ea1b157d105a8bdcbd5bf3848a2105fd994684ce3bf", digest)
            for kind, table, _, _ in objects:
                if kind == "table" and table != "room_master_table":
                    self.assertEqual(0, db.execute('SELECT COUNT(*) FROM "' + table + '"').fetchone()[0], table)
            columns = {row[1]: row for row in db.execute("PRAGMA table_info(chapter_content)")}
            self.assertEqual("1", columns["contentVersion"][4])
            self.assertNotIn("chapter_download_state", {row[0] for row in db.execute("SELECT name FROM sqlite_master")})


if __name__ == "__main__":
    unittest.main()
