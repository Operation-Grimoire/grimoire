import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import release_policy as policy


class ImpactTest(unittest.TestCase):
    def test_platform_and_shared_matrix(self):
        cases = {
            "app/src/main/java/Reader.kt": (True, False, True, False),
            "app/src/main/assets/help.md": (True, False, True, False),
            "desktop/src/main/kotlin/Main.kt": (False, True, False, True),
            "reading-core/src/main/kotlin/Codec.kt": (True, True, True, True),
            "gradle/libs.versions.toml": (True, True, True, True),
            "new-module/src/main/Runtime.kt": (True, True, True, True),
            "app/src/test/java/ReaderTest.kt": (True, False, False, False),
            "app/src/androidTest/java/HostTest.kt": (True, False, False, False),
            "desktop/src/integrationTest/kotlin/Journey.kt": (False, True, False, False),
            "reading-core/src/test/kotlin/CodecTest.kt": (True, True, False, False),
            ".github/workflows/desktop.yml": (False, True, False, False),
            ".github/scripts/release_policy.py": (True, True, False, False),
            "docs/desktop.md": (False, False, False, False),
            ".changes/410-platforms.json": (False, False, False, False),
        }
        for path, expected in cases.items():
            with self.subTest(path=path):
                actual = policy.impact([path])
                self.assertEqual(expected, tuple(actual[key] for key in
                    ("android_ci", "desktop_ci", "android_release", "desktop_release")))

    def test_mixed_changes_combine_platforms(self):
        actual = policy.impact(["app/src/main/A.kt", "desktop/src/test/Test.kt"])
        self.assertEqual({"android_ci": True, "desktop_ci": True,
                          "android_release": True, "desktop_release": False}, actual)


class GitPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.old = os.getcwd()
        os.chdir(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.addCleanup(os.chdir, self.old)
        self.run_git("init", "-q")
        self.run_git("config", "user.email", "test@example.invalid")
        self.run_git("config", "user.name", "Test")
        self.write("README.md", "test")
        self.base = self.save()

    def run_git(self, *args):
        return subprocess.check_output(["git", *args], stderr=subprocess.PIPE).decode().strip()

    def write(self, path, content):
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        Path(path).write_text(content, encoding="utf-8")

    def save(self):
        self.run_git("add", ".")
        self.run_git("commit", "-qm", "fixture")
        return self.run_git("rev-parse", "HEAD")

    def fragment(self, name, android=(), desktop=()):
        value = {"android": list(android), "desktop": list(desktop)}
        self.write(f".changes/{name}.json", json.dumps(value))

    def release(self, tag, *, complete=True, draft=False):
        self.run_git("tag", tag)
        assets = [{"name": "grimoire.apk", "state": "uploaded", "size": 100}]
        if complete:
            assets.append({"name": "grimoire.apk.sha256", "state": "uploaded", "size": 65})
        return {"tag_name": tag, "prerelease": "-beta." in tag,
                "draft": draft, "assets": assets}

    def test_mixed_notes_have_separate_audiences_and_internal_is_blank(self):
        self.fragment("410-platforms", [{"category": "fix", "text": "Phone updates stay on Android."}],
                      [{"category": "fix", "text": "Desktop loads the supplied extension."}])
        self.write("app/src/main/Update.kt", "runtime")
        self.write("desktop/src/main/Main.kt", "runtime")
        head = self.save()
        self.assertTrue(policy.validate(self.base, head)["android_release"])
        self.assertEqual("## Bug fixes\n\n- Phone updates stay on Android.", policy.notes(self.base, head, "android"))
        self.assertEqual("## Bug fixes\n\n- Desktop loads the supplied extension.", policy.notes(self.base, head, "desktop"))
        self.fragment("411-internal")
        self.write("app/src/main/Update.kt", "internal")
        internal = self.save()
        policy.validate(head, internal)
        self.assertEqual("", policy.notes(head, internal, "android"))

    def test_fragments_survive_independent_beta_and_stable_ranges(self):
        self.fragment("1-shared", [{"category": "feature", "text": "Shared reading."}],
                      [{"category": "feature", "text": "Shared reading on desktop."}])
        first = self.save()
        self.fragment("2-desktop", desktop=[{"category": "fix", "text": "Desktop correction."}])
        second = self.save()
        self.assertEqual("", policy.notes(first, second, "android"))
        stable = policy.notes(self.base, second, "android")
        self.assertIn("Shared reading.", stable)
        self.assertNotIn("Desktop", stable)
        desktop = policy.notes(self.base, second, "desktop")
        self.assertIn("Shared reading on desktop.", desktop)
        self.assertIn("Desktop correction.", desktop)

    def test_runtime_without_fragment_fails_but_test_only_does_not(self):
        self.write("desktop/src/test/Reader.kt", "test")
        tests = self.save()
        self.assertFalse(policy.validate(self.base, tests)["desktop_release"])
        self.write("desktop/src/main/Reader.kt", "runtime")
        head = self.save()
        with self.assertRaisesRegex(ValueError, "need a new"):
            policy.validate(tests, head)

    def test_modified_and_deleted_fragments_are_rejected(self):
        self.fragment("1-original")
        first = self.save()
        self.fragment("1-original", [{"category": "fix", "text": "Rewritten history"}])
        modified = self.save()
        with self.assertRaisesRegex(ValueError, "immutable"):
            policy.validate(first, modified)
        Path(".changes/1-original.json").unlink()
        deleted = self.save()
        with self.assertRaisesRegex(ValueError, "immutable"):
            policy.validate(modified, deleted)

    def test_malformed_fragment_is_rejected(self):
        for i, value in enumerate([{"android": []}, {"android": [], "desktop": "none"},
                {"android": [{"category": "bogus", "text": "bad"}], "desktop": []},
                {"android": [{"category": "fix", "text": "two\nlines"}], "desktop": []}]):
            path = f".changes/{i}-bad.json"
            self.write(path, json.dumps(value))
            head = self.save()
            with self.assertRaises(ValueError):
                policy.validate(self.run_git("rev-parse", "HEAD^"), head)

    def test_previous_uses_platform_channel_complete_assets_and_ancestry(self):
        stable = self.release("v1.0.5")
        self.write("app/src/main/A.kt", "beta")
        beta_sha = self.save()
        beta = self.release("v1.0.5-beta.341")
        desktop = self.release("desktop-v9.0.0")
        incomplete = self.release("v1.0.5-beta.342", complete=False)
        draft = self.release("v1.0.5-beta.343", draft=True)
        self.write("app/src/main/A.kt", "future")
        self.save()
        future = self.release("v1.0.6")
        catalog = [[desktop, future], [incomplete, draft, beta, stable]]
        self.assertEqual("v1.0.5-beta.341", policy.previous(catalog, "android", "beta", beta_sha))
        self.assertEqual("v1.0.5", policy.previous(catalog, "android", "stable", beta_sha))
        self.assertEqual("desktop-v9.0.0", policy.previous(catalog, "desktop", "stable", beta_sha))
        self.assertEqual("v1.0.5", policy.previous(catalog, "android", "beta", beta_sha, beta["tag_name"]))

    def test_missing_published_tag_fails_instead_of_widening_release_range(self):
        published = {"tag_name": "v1.0.5", "assets": [{"name": "grimoire.apk", "state": "uploaded", "size": 100}]}
        with self.assertRaisesRegex(RuntimeError, "Missing local tag"):
            policy.previous([[published]], "android", "beta", self.base)

    def test_started_or_empty_upload_does_not_advance_published_boundary(self):
        stable = self.release("v1.0.5", complete=False)
        beta = self.release("v1.0.5-beta.341")
        for asset in range(len(beta["assets"])):
            for field, value in (("state", "starter"), ("size", 0)):
                partial = json.loads(json.dumps(beta))
                partial["assets"][asset][field] = value
                with self.subTest(asset=beta["assets"][asset]["name"], field=field):
                    self.assertEqual("v1.0.5", policy.previous([[partial, stable]], "android", "beta", self.base))

    def test_plan_rejects_runtime_without_fragment_even_when_forced(self):
        self.release("v1.0.5-beta.341")
        self.write("app/src/main/Reader.kt", "runtime without notes classification")
        self.save()
        self.write("catalog.json", "[]")
        script = Path(__file__).with_name("release_policy.py")
        result = subprocess.run([os.sys.executable, str(script), "plan", "--platform", "android",
            "--channel", "beta", "--catalog", "catalog.json", "--notes", "notes.md", "--force"],
            capture_output=True, text=True,
            env={key: value for key, value in os.environ.items() if key != "GITHUB_OUTPUT"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("need a new", result.stderr)
        self.assertFalse(Path("notes.md").exists())

    def test_rename_between_platforms_counts_both(self):
        self.write("app/src/main/Shared.kt", "runtime")
        first = self.save()
        self.run_git("mv", "app", "desktop")
        head = self.save()
        affected = policy.impact(path for _, path in policy.changes(first, head))
        self.assertTrue(affected["android_release"])
        self.assertTrue(affected["desktop_release"])

    def test_desktop_merge_keeps_unpublished_android_change_eligible(self):
        self.release("v1.0.5-beta.341")
        self.write("app/src/main/A.kt", "unpublished phone change")
        phone = self.save()
        self.write("desktop/src/main/B.kt", "desktop change")
        head = self.save()
        self.assertFalse(policy.impact(path for _, path in policy.changes(phone, head))["android_release"])
        self.assertTrue(policy.impact(path for _, path in policy.changes(self.base, head))["android_release"])

    def test_plan_never_publishes_desktop_or_claims_latest(self):
        self.write("desktop/src/main/Main.kt", "runtime")
        self.fragment("1-desktop", desktop=[{"category": "feature", "text": "Desktop reader."}])
        self.save()
        self.write("catalog.json", "[]")
        script = Path(__file__).with_name("release_policy.py")
        result = subprocess.check_output([os.sys.executable, str(script), "plan", "--platform", "desktop",
            "--channel", "beta", "--catalog", "catalog.json", "--notes", "notes.md", "--force"],
            env={key: value for key, value in os.environ.items() if key != "GITHUB_OUTPUT"}).decode()
        self.assertIn("desktop_release=true", result)
        self.assertIn("publish=false", result)
        self.assertIn("make_latest=false", result)
        self.assertEqual("## Features\n\n- Desktop reader.", Path("notes.md").read_text())

    def test_plan_skips_phone_beta_after_desktop_only_merge(self):
        previous = self.release("v1.0.5-beta.341")
        self.write("desktop/src/main/Main.kt", "desktop runtime")
        self.fragment("1-desktop", desktop=[{"category": "fix", "text": "Desktop correction."}])
        self.save()
        self.write("catalog.json", json.dumps([[previous]]))
        script = Path(__file__).with_name("release_policy.py")
        command = [os.sys.executable, str(script), "plan", "--platform", "android", "--channel", "beta",
                   "--catalog", "catalog.json", "--notes", "notes.md"]
        env = {key: value for key, value in os.environ.items() if key != "GITHUB_OUTPUT"}
        normal = subprocess.check_output(command, env=env).decode()
        self.assertIn("publish=false", normal)
        self.assertIn("previous=v1.0.5-beta.341", normal)
        self.assertEqual("", Path("notes.md").read_text())
        forced = subprocess.check_output(command + ["--force"], env=env).decode()
        self.assertIn("publish=true", forced)
        self.assertIn("make_latest=false", forced)


class ReleaseMetadataTest(unittest.TestCase):
    def test_unrelated_and_inconsistent_tags_are_rejected(self):
        for tag in ("v1.0.5-alpha.1", "v01.0.5", "desktop-v9.0.0", "not-a-version"):
            self.assertIsNone(policy.release_key({"tag_name": tag}, "android", "beta"))
        self.assertIsNone(policy.release_key({"tag_name": "v1.0.5-beta.341", "prerelease": False}, "android", "beta"))


if __name__ == "__main__":
    unittest.main()
