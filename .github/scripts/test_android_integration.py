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


def events(class_name=runner.CLASS, tests=runner.TESTS):
    result = []
    for name in tests:
        for code in (1, 0):
            result.extend([f"INSTRUMENTATION_STATUS: class={class_name}", f"INSTRUMENTATION_STATUS: test={name}",
                           f"INSTRUMENTATION_STATUS: numtests={len(tests)}", f"INSTRUMENTATION_STATUS_CODE: {code}"])
    summary = "1 test" if len(tests) == 1 else f"{len(tests)} tests"
    return "\n".join(result) + f"\nOK ({summary})\nINSTRUMENTATION_CODE: -1\n"


INTERRUPTED = (
    f"INSTRUMENTATION_STATUS: class={runner.CHECKPOINT_CLASS}\n"
    f"INSTRUMENTATION_STATUS: test={runner.INTERRUPT}\n"
    "INSTRUMENTATION_STATUS: numtests=1\n"
    "INSTRUMENTATION_STATUS_CODE: 1\n"
    "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n"
    "INSTRUMENTATION_CODE: 0"
)


class IntegrationResultTest(unittest.TestCase):
    def test_complete_exact_inventory(self):
        self.assertEqual(sorted(runner.TESTS), runner.parse_instrumentation(events()))

    def test_app_and_test_helper_crashes_fail_even_after_successful_statuses(self):
        self.assertEqual(sorted(runner.TESTS), runner.parse_instrumentation(events()))
        for package in (runner.PACKAGE, runner.PACKAGE + ".test", runner.PACKAGE + ":checkpoint_fixture"):
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
                    with (patch.dict(os.environ, {"INTEGRATION_SOURCE_SHA": "a" * 40}),
                          patch.object(runner.subprocess, "run", side_effect=command),
                          patch.object(runner, "run_cold_launch_smoke", return_value={"pid": 123}),
                          patch.object(runner, "run_checkpoint_interruptions", return_value={}),
                          patch.object(runner, "run_checkpoint_competition", return_value={}),
                          contextlib.redirect_stdout(io.StringIO())):
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

    def test_expected_checkpoint_process_exit_requires_its_exact_start_and_terminal(self):
        runner.validate_checkpoint_exit(INTERRUPTED)
        runner.validate_checkpoint_exit(INTERRUPTED.replace("\n", "\r\n"))
        for output in (
            INTERRUPTED.replace(runner.INTERRUPT, runner.REOPEN),
            INTERRUPTED.replace("Process crashed.", "Process failed to start."),
            INTERRUPTED.replace("STATUS_CODE: 1", "STATUS_CODE: 0"),
            INTERRUPTED.replace("CODE: 0", "CODE: -1"),
            events(runner.CHECKPOINT_CLASS, (runner.INTERRUPT,)),
        ):
            with self.assertRaises(ValueError):
                runner.validate_checkpoint_exit(output)

    def test_checkpoint_readback_checks_both_expected_positions_and_new_pids(self):
        sha = "a" * 40
        faults = (
            (None, {}),
            ("reopened", {"pid": 123}),
            ("reopened", {"ordinal": 3}),
            ("reopened", {"fixture": "stale"}),
            ("reopened", {"sourceSha": "wrong"}),
            ("boundary", {"pendingOrdinal": 1}),
            ("boundary", {"writeStarted": True}),
        )
        for phase, fault in faults:
            boundary = fixture = None

            def adb(*args, **kwargs):
                nonlocal boundary, fixture
                if "instrument" in args:
                    boundary = args[args.index("checkpointCrashBoundary") + 1]
                    fixture = args[args.index("checkpointCrashFixture") + 1]
                    if f"{runner.CHECKPOINT_CLASS}#{runner.INTERRUPT}" in args:
                        return INTERRUPTED
                    return events(runner.CHECKPOINT_CLASS, (runner.REOPEN,))
                if args[-1].endswith("-boundary.json"):
                    stage = runner.CHECKPOINT_BOUNDARIES[boundary][0]
                    marker = {"pid": 123, "sourceSha": sha, "boundary": boundary, "fixture": fixture, "stage": stage}
                    if boundary == "during-debounce":
                        marker.update(pendingOrdinal=3, writeStarted=False)
                    return json.dumps(marker | (fault if phase == "boundary" else {}))
                if args[-1].endswith("-reopened.json"):
                    ordinal = runner.CHECKPOINT_BOUNDARIES[boundary][1]
                    marker = {"pid": 456, "sourceSha": sha, "boundary": boundary, "fixture": fixture,
                              "ordinal": ordinal, "neighborOrdinal": 2, "oldPid": 123}
                    return json.dumps(marker | (fault if phase == "reopened" else {}))
                if "pidof" in args[-1]:
                    return "stopped"
                return ""

            with tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                if phase is not None:
                    with self.assertRaises(ValueError):
                        runner.run_checkpoint_interruptions(adb, root, sha)
                else:
                    result = runner.run_checkpoint_interruptions(adb, root, sha)
                    self.assertEqual(["during-debounce", "before-commit", "before-ack"], list(result))
                    self.assertEqual([1, 1, 3], [case["reopened"]["ordinal"] for case in result.values()])
                    self.assertEqual(6, len(list(root.glob("*.json"))))

    def test_competing_checkpoint_proof_requires_distinct_pids_and_exact_outcome(self):
        sha = "a" * 40
        for fault in (None, {"remotePid": 123}, {"winnerSequence": 2}, {"rejection": "CONFLICT"}, {"fixture": "stale"}):
            fixture = None

            def adb(*args, **kwargs):
                nonlocal fixture
                if "instrument" in args:
                    fixture = args[args.index("checkpointCrashFixture") + 1]
                    return events(runner.CHECKPOINT_CLASS, (runner.COMPETE,))
                if args[-1].endswith("-competition.json"):
                    marker = {"sourceSha": sha, "boundary": "competing-writer", "fixture": fixture,
                              "pid": 123, "remotePid": 456, "oldOrdinal": 3, "winnerOrdinal": 1, "neighborOrdinal": 2,
                              "winnerSequence": 1, "staleSequence": 2, "rejection": "STALE"}
                    return json.dumps(marker | (fault or {}))
                return ""

            with tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                if fault is None:
                    result = runner.run_checkpoint_competition(adb, root, sha)
                    self.assertEqual(456, result["remotePid"])
                    self.assertEqual("STALE", result["rejection"])
                else:
                    with self.assertRaises(ValueError):
                        runner.run_checkpoint_competition(adb, root, sha)


    def test_cold_launch_smoke_requires_live_uninstrumented_process_without_crash(self):
        cases = (
            (None, "Status: ok\nActivity: io.grimoire.app/.MainActivity", ["420", "420"], "I ActivityTaskManager: Displayed"),
            ("start-error", "Status: ok\nError: Activity class does not exist.", ["420", "420"], "I ActivityTaskManager: Displayed"),
            ("missing-status", "Starting: Intent { cmp=io.grimoire.app/.MainActivity }", ["420", "420"], "I ActivityTaskManager: Displayed"),
            ("exited", "Status: ok", ["420", ""], "I ActivityTaskManager: Displayed"),
            ("restarted", "Status: ok", ["420", "421"], "I ActivityTaskManager: Displayed"),
            ("crashed", "Status: ok", ["420", "420"], f"E AndroidRuntime: Process: {runner.PACKAGE}, PID: 420\nIllegalStateException"),
        )
        for label, start_out, pids, logcat in cases:
            pid_iter = iter(pids)
            calls = []

            def adb(*args, **kwargs):
                calls.append(args)
                if args[:3] == ("shell", "am", "start"):
                    return start_out
                if args[:2] == ("shell", "pidof"):
                    return next(pid_iter)
                if args[:2] == ("logcat", "-d"):
                    return logcat
                return ""

            with tempfile.TemporaryDirectory() as folder:
                if label is None:
                    result = runner.run_cold_launch_smoke(adb, Path(folder), settle_seconds=0)
                    self.assertEqual({"pid": 420}, result)
                    self.assertEqual(("shell", "am", "force-stop", runner.PACKAGE), calls[0])
                    self.assertEqual(("shell", "am", "force-stop", runner.PACKAGE), calls[-1])
                else:
                    with self.assertRaises(ValueError, msg=label):
                        runner.run_cold_launch_smoke(adb, Path(folder), settle_seconds=0)


if __name__ == "__main__":
    unittest.main()
