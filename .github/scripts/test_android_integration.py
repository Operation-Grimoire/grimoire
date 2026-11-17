import importlib.util
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("android_integration", Path(__file__).with_name("android_integration.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


def events():
    result = []
    for name in runner.TESTS:
        for code in (1, 0):
            result.extend([f"INSTRUMENTATION_STATUS: class={runner.CLASS}", f"INSTRUMENTATION_STATUS: test={name}",
                           f"INSTRUMENTATION_STATUS: numtests={len(runner.TESTS)}", f"INSTRUMENTATION_STATUS_CODE: {code}"])
    return "\n".join(result) + f"\nOK ({len(runner.TESTS)} tests)\nINSTRUMENTATION_CODE: -1\n"


class IntegrationResultTest(unittest.TestCase):
    def test_complete_exact_inventory(self):
        self.assertEqual(sorted(runner.TESTS), runner.parse_instrumentation(events()))

    def test_app_and_test_helper_crashes_fail_even_after_successful_statuses(self):
        self.assertEqual(sorted(runner.TESTS), runner.parse_instrumentation(events()))
        for package in (runner.PACKAGE, runner.PACKAGE + ".test"):
            with self.assertRaises(ValueError):
                runner.validate_app_logcat(f"E AndroidRuntime: Process: {package}, PID: 123\njava.lang.NoClassDefFoundError: ActivityInvoker")
        runner.validate_app_logcat("E AndroidRuntime: Process: com.example.system, PID: 123")
        runner.validate_app_logcat(f"E AndroidRuntime: Process: {runner.PACKAGE}lication, PID: 123")

    def test_missing_logcat_cannot_establish_crash_free_helper_execution(self):
        with self.assertRaises(ValueError):
            runner.validate_app_logcat("")

    def test_failed_logcat_exit_rejects_partial_output_and_preserves_primary_failure(self):
        self.assert_logcat_failure(False)

    def test_logcat_timeout_retains_partial_output_and_preserves_primary_failure(self):
        self.assert_logcat_failure(True)

    def assert_logcat_failure(self, timeout):
        for instrument in (events(), "INSTRUMENTATION_FAILED: primary failure"):
            def command(args, **kwargs):
                tail = args[1:]
                output = b""; code = 0
                if tail == ["shell", "getprop", "sys.boot_completed"] or tail == ["shell", "getprop", "ro.kernel.qemu"]:
                    output = b"1"
                elif tail == ["get-serialno"]: output = b"emulator-5554"
                elif tail == ["shell", "getprop", "ro.build.version.sdk"]: output = b"35"
                elif tail[:3] == ["shell", "pm", "clear"]: output = b"Success"
                elif tail[:3] == ["shell", "settings", "get"]: output = b"1"
                elif "instrument" in tail: output = instrument.encode()
                elif tail[:2] == ["logcat", "-d"]:
                    if timeout:
                        raise subprocess.TimeoutExpired(args, 90, output=b"Partial logcat output without a crash")
                    code = 1; output = b"Partial logcat output without a crash"
                return subprocess.CompletedProcess(args, code, output, b"capture failed" if code else b"")
            previous = Path.cwd()
            with tempfile.TemporaryDirectory() as directory:
                try:
                    os.chdir(directory)
                    root = Path("build/android-integration"); root.mkdir(parents=True)
                    digest = hashlib.sha256(b"fixture apk").hexdigest()
                    for name in ("host.apk", "tests.apk"): (root / name).write_bytes(b"fixture apk")
                    (root / "sha256sums.txt").write_text(f"{digest} host.apk\n{digest} tests.apk\n", encoding="utf-8")
                    with patch.dict(os.environ, {"INTEGRATION_SOURCE_SHA": "a" * 40}), patch.object(runner.subprocess, "run", side_effect=command), contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(1, runner.main())
                    report = json.loads((root / "report.json").read_text(encoding="utf-8"))
                    self.assertEqual("FAIL", report["result"])
                    self.assertTrue((root / "logcat.log").exists(), "Keep partial failed-capture evidence")
                    self.assertEqual(b"Partial logcat output without a crash", (root / "logcat.log").read_bytes())
                    expected = "timed out" if timeout else "ADB command failed"
                    self.assertIn(expected, report["logcat.log"])
                    if instrument == events(): self.assertIn(expected, report["error"])
                    else: self.assertIn("Incomplete executed inventory", report["error"])
                finally:
                    os.chdir(previous)

    def test_zero_and_partial_inventory_fail(self):
        for output in ["OK (3 tests)\nINSTRUMENTATION_CODE: -1", events().replace(runner.TESTS[0], "missing")]:
            with self.assertRaises(ValueError):
                runner.parse_instrumentation(output)

    def test_skips_assumptions_failures_and_ignored_tests_fail(self):
        for code in [-1, -2, -3, -4, 2]:
            with self.assertRaises(ValueError):
                runner.parse_instrumentation(events().replace("INSTRUMENTATION_STATUS_CODE: 0", f"INSTRUMENTATION_STATUS_CODE: {code}", 1))

    def test_duplicate_or_unpaired_completion_fails(self):
        for output in [events().replace("INSTRUMENTATION_STATUS_CODE: 1", "INSTRUMENTATION_STATUS_CODE: 0", 1),
                       events() + events()]:
            with self.assertRaises(ValueError):
                runner.parse_instrumentation(output)

    def test_wrong_class_count_or_incomplete_finish_fails(self):
        for output in [events().replace(runner.CLASS, "another.Class"), events().replace(f"numtests={len(runner.TESTS)}", "numtests=99"),
                       events().replace("INSTRUMENTATION_CODE: -1", ""), events().replace(f"OK ({len(runner.TESTS)} tests)", "")]:
            with self.assertRaises(ValueError):
                runner.parse_instrumentation(output)


if __name__ == "__main__":
    unittest.main()
