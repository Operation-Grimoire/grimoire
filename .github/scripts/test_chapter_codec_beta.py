import os
from pathlib import Path
import subprocess
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


if __name__ == "__main__":
    unittest.main()
