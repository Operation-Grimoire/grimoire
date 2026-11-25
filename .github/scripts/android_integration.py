"""Run the owned-emulator production journeys and require every named test to execute."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

CLASS = "io.grimoire.app.integration.ReaderIntegrationTest"
TESTS = (
    "inputBackColdReopenAndRecreationRestoreOfflinePosition",
    "restoredEndDoesNotMarkReadUntilExplicitNextNavigation",
    "failedCheckpointSaveCanBeDismissedAndRetriedWithoutLosingInput",
    "manualReadFailureRetriesExactCommandAndUnreadSurvivesReopen",
    "partialWordCountRetryPreservesLaterExplicitUnread",
    "visualCompletionNeedsFreshScrollAfterLayoutAndExplicitUnread",
    "detailMarkAllRetriesAtomicWriteThenRetriesOnlyWordCount",
    "detailSelectedChaptersUseGuardedCompletion",
    "librarySelectedNovelsUseGuardedCompletion",
    "updatesSelectedEntriesUseGuardedCompletion",
    "privateAndFailedOpensRespectOrderedHistoryClear",
)
PACKAGE = "io.grimoire.app"
CHECKPOINT_CLASS = "io.grimoire.app.integration.CheckpointProcessDeathTest"
INTERRUPT = "interruptCheckpointSave"
REOPEN = "verifyCheckpointAfterRestart"
COMPETE = "competingProcessCannotReplaceNewerCheckpoint"
CHECKPOINT_BOUNDARIES = {
    "during-debounce": ("pending", 1),
    "before-commit": ("uncommitted", 1),
    "before-ack": ("committed", 3),
}


def validate_app_logcat(output):
    if not output.strip():
        raise ValueError("Missing application logcat evidence")
    if re.search(rf"AndroidRuntime:\s+Process: {re.escape(PACKAGE)}(?:\.test)?(?::[^\s,]+)?, PID:", output):
        raise ValueError("Application/test-helper process crashed; successful test statuses are insufficient")


def instrumentation_events(output):
    bundle = {}
    for line in output.splitlines():
        field = re.fullmatch(r"INSTRUMENTATION_STATUS: ([^=]+)=(.*)", line)
        if field:
            bundle[field[1]] = field[2]
        status = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line)
        if not status:
            continue
        yield bundle, int(status[1])
        bundle = {}


def parse_instrumentation(output, class_name=CLASS, tests=TESTS):
    """Reject skips, wrong inventory, duplicate completions and incomplete runner output."""
    expected = {(class_name, name) for name in tests}
    started, succeeded = set(), set()
    for bundle, code in instrumentation_events(output):
        case = (bundle.get("class"), bundle.get("test"))
        if case not in expected or code not in (0, 1):
            raise ValueError(f"Unexpected/failed/skipped instrumentation event: {case}, code {code}")
        if "numtests" in bundle and bundle["numtests"] != str(len(tests)):
            raise ValueError("Instrumentation inventory count differs")
        if code == 1:
            if case in started:
                raise ValueError(f"Duplicate test start: {case}")
            started.add(case)
        else:
            if case not in started or case in succeeded:
                raise ValueError(f"Unpaired or duplicate success: {case}")
            succeeded.add(case)
    if started != expected or succeeded != expected:
        raise ValueError(f"Incomplete executed inventory: started={started}, succeeded={succeeded}")
    summary = "1 test" if len(tests) == 1 else f"{len(tests)} tests"
    if (not re.search(rf"^OK \({summary}\)\s*$", output, re.M)
            or len(re.findall(r"^INSTRUMENTATION_CODE: -1\s*$", output, re.M)) != 1
            or re.search(r"FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|shortMsg=|Process crashed", output)):
        raise ValueError("Instrumentation did not complete successfully")
    return sorted(name for _, name in succeeded)


def validate_checkpoint_exit(output):
    events = list(instrumentation_events(output))
    lines = output.splitlines()
    starts = [(bundle.get("class"), bundle.get("test"), bundle.get("numtests"), code) for bundle, code in events]
    if (starts != [(CHECKPOINT_CLASS, INTERRUPT, "1", 1)]
            or [line for line in lines if line.startswith("INSTRUMENTATION_RESULT: shortMsg=")]
            != ["INSTRUMENTATION_RESULT: shortMsg=Process crashed."]
            or [line for line in lines if line.startswith("INSTRUMENTATION_CODE: ")] != ["INSTRUMENTATION_CODE: 0"]
            or re.search(r"FAILURES|INSTRUMENTATION_(FAILED|ABORTED)", output)):
        raise ValueError("Expected one interrupted checkpoint test with an intentional process exit")


def validate_checkpoint_evidence(marker, sha, boundary, fixture, **expected):
    identity = {"sourceSha": sha, "boundary": boundary, "fixture": fixture, **expected}
    if (not isinstance(marker, dict) or type(marker.get("pid")) is not int or marker["pid"] <= 0
            or any(marker.get(key) != value for key, value in identity.items())):
        raise ValueError(f"Checkpoint evidence did not match {identity}")


def instrument_checkpoint(adb, method, boundary, fixture, sha, log_name, required=True):
    return adb("shell", "am", "instrument", "-w", "-r", "-e", "class", f"{CHECKPOINT_CLASS}#{method}",
               "-e", "integrationFixtures", "true", "-e", "integrationSourceSha", sha,
               "-e", "checkpointCrashBoundary", boundary, "-e", "checkpointCrashFixture", fixture,
               PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=65, required=required,
               log_name=log_name)


def run_checkpoint_interruptions(adb, root, sha):
    results = {}

    for boundary, (stage, ordinal) in CHECKPOINT_BOUNDARIES.items():
        fixture = uuid.uuid4().hex
        adb("shell", "am", "force-stop", PACKAGE)
        validate_checkpoint_exit(instrument_checkpoint(
            adb, INTERRUPT, boundary, fixture, sha, f"checkpoint-{boundary}-interrupted.log", required=False))
        if adb("shell", f"if pidof {PACKAGE}; then exit 1; else echo stopped; fi") != "stopped":
            raise ValueError(f"Checkpoint process is still running at {boundary}")
        interrupted = json.loads(adb("shell", "run-as", PACKAGE, "cat", f"files/checkpoint-{fixture}-boundary.json"))
        (root / f"checkpoint-{boundary}-boundary.json").write_text(json.dumps(interrupted, indent=2), encoding="utf-8")
        validate_checkpoint_evidence(interrupted, sha, boundary, fixture, stage=stage)
        if boundary == "during-debounce" and (interrupted.get("pendingOrdinal") != 3 or interrupted.get("writeStarted") is not False):
            raise ValueError("Expected block 3 pending before the checkpoint writer started")
        parse_instrumentation(instrument_checkpoint(
            adb, REOPEN, boundary, fixture, sha, f"checkpoint-{boundary}-verified.log"), CHECKPOINT_CLASS, (REOPEN,))
        reopened = json.loads(adb("shell", "run-as", PACKAGE, "cat", f"files/checkpoint-{fixture}-reopened.json"))
        (root / f"checkpoint-{boundary}-reopened.json").write_text(json.dumps(reopened, indent=2), encoding="utf-8")
        validate_checkpoint_evidence(reopened, sha, boundary, fixture, oldPid=interrupted["pid"], ordinal=ordinal, neighborOrdinal=2)
        if reopened["pid"] == interrupted["pid"]:
            raise ValueError(f"Checkpoint verification reused the old process at {boundary}")
        results[boundary] = {"interrupted": interrupted, "reopened": reopened}
    return results


def run_cold_launch_smoke(adb, root, settle_seconds=3):
    """Launch MainActivity without instrumentation so host-only crashes are not masked."""
    adb("shell", "am", "force-stop", PACKAGE)
    start_output = adb(
        "shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity",
        log_name="cold-launch.log",
    )
    if not re.search(r"^Status:\s*ok\s*$", start_output, re.M) or re.search(r"^Error:", start_output, re.M):
        raise ValueError(f"Uninstrumented MainActivity launch failed: {start_output}")
    initial_pid = adb("shell", "pidof", PACKAGE, required=False).split()
    if len(initial_pid) != 1 or not initial_pid[0].isdigit():
        raise ValueError(f"Expected one live uninstrumented process after launch, got: {initial_pid}")
    if settle_seconds > 0:
        time.sleep(settle_seconds)
    settled_pid = adb("shell", "pidof", PACKAGE, required=False).split()
    if settled_pid != initial_pid:
        raise ValueError(f"Uninstrumented process exited or restarted during settle: {initial_pid} -> {settled_pid}")
    validate_app_logcat(adb("logcat", "-d", "-v", "threadtime"))
    adb("shell", "am", "force-stop", PACKAGE)
    return {"pid": int(initial_pid[0])}


def run_checkpoint_competition(adb, root, sha):
    fixture = uuid.uuid4().hex
    boundary = "competing-writer"
    adb("shell", "am", "force-stop", PACKAGE)
    output = instrument_checkpoint(adb, COMPETE, boundary, fixture, sha, "checkpoint-competition.log")
    parse_instrumentation(output, CHECKPOINT_CLASS, (COMPETE,))
    marker = json.loads(adb("shell", "run-as", PACKAGE, "cat", f"files/checkpoint-{fixture}-competition.json"))
    (root / "checkpoint-competition.json").write_text(json.dumps(marker, indent=2), encoding="utf-8")
    validate_checkpoint_evidence(marker, sha, boundary, fixture, oldOrdinal=3, winnerOrdinal=1, neighborOrdinal=2,
                                 winnerSequence=1, staleSequence=2, rejection="STALE")
    if type(marker.get("remotePid")) is not int or marker["remotePid"] <= 0 or marker["remotePid"] == marker["pid"]:
        raise ValueError("The competing checkpoint writer must run in a separate process")
    return marker


def main():
    root = Path("build/android-integration")
    root.mkdir(parents=True, exist_ok=True)
    commands = []
    owned = False
    report = {"result": "FAIL", "expectedClass": CLASS, "expectedTests": list(TESTS)}

    def adb(*args, timeout=90, required=True, binary=False, log_name=None):
        command = ["adb", *args]
        commands.append(command)
        try:
            result = subprocess.run(command, capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired as failure:
            if log_name:
                (root / log_name).write_bytes(failure.stdout or b"")
            elif args[:2] == ("logcat", "-d"):
                (root / "logcat.log").write_bytes(failure.stdout or b"")
            raise
        if log_name:
            (root / log_name).write_bytes(result.stdout)
        elif args[:2] == ("logcat", "-d"):
            (root / "logcat.log").write_bytes(result.stdout)
        if required and result.returncode:
            raise RuntimeError(f"ADB command failed: {args}: {result.stderr.decode(errors='replace')}")
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace").strip()

    try:
        sha = os.environ.get("INTEGRATION_SOURCE_SHA", "")
        if not re.fullmatch(r"[a-f0-9]{40}", sha):
            raise ValueError("INTEGRATION_SOURCE_SHA must name the exact built commit")
        report["sourceSha"] = sha
        deadline = time.monotonic() + 90
        while adb("shell", "getprop", "sys.boot_completed", required=False) != "1":
            if time.monotonic() >= deadline:
                raise RuntimeError("Emulator did not finish boot within 90 seconds")
            time.sleep(2)
        if not adb("get-serialno").startswith("emulator-") or adb("shell", "getprop", "ro.kernel.qemu") != "1":
            raise RuntimeError("Refusing fixture setup on a non-emulator device")
        owned = True
        report["api"] = adb("shell", "getprop", "ro.build.version.sdk")
        if report["api"] != "35":
            raise RuntimeError("The maintained integration image is API 35")
        report["apkSha256"] = {}
        sums = {}
        for line in (root / "sha256sums.txt").read_text(encoding="utf-8").splitlines():
            digest, filename = line.split()
            sums[filename] = digest
        if set(sums) != {"host.apk", "tests.apk"}:
            raise ValueError("Expected exactly the host and test APK artifacts")
        for name in ("host.apk", "tests.apk"):
            digest = hashlib.sha256((root / name).read_bytes()).hexdigest()
            if digest != sums[name]:
                raise ValueError(f"APK checksum mismatch: {name}")
            report["apkSha256"][name] = digest
            adb("install", "-r", "-t", str(root / name))
        if adb("shell", "pm", "clear", PACKAGE) != "Success":
            raise RuntimeError("Could not clear owned fixture app data")
        adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
        if adb("shell", "settings", "get", "global", "airplane_mode_on") != "1":
            raise RuntimeError("Emulator did not accept airplane mode")
        adb("shell", "svc", "wifi", "disable")
        adb("shell", "svc", "data", "disable")
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb("shell", "wm", "dismiss-keyguard")
        adb("logcat", "-c")
        output = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", CLASS,
                     "-e", "integrationFixtures", "true", "-e", "integrationSourceSha", sha,
                     PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=240, log_name="instrumentation.log")
        report["executedTests"] = parse_instrumentation(output)
        report["coldLaunch"] = run_cold_launch_smoke(adb, root)
        report["checkpointInterruptions"] = run_checkpoint_interruptions(adb, root, sha)
        report["checkpointCompetition"] = run_checkpoint_competition(adb, root, sha)
        report["result"] = "PASS"
    except Exception as failure:
        report["error"] = str(failure)
    finally:
        if owned:
            for filename, args in [("logcat.log", ("logcat", "-d", "-v", "threadtime")),
                                   ("connectivity.log", ("shell", "dumpsys", "connectivity")),
                                   ("activity.log", ("shell", "dumpsys", "activity", "activities"))]:
                try:
                    output = adb(*args, required=filename == "logcat.log")
                    (root / filename).write_text(output, encoding="utf-8")
                    if filename == "logcat.log":
                        validate_app_logcat(output)
                except Exception as failure:
                    report[filename] = str(failure)
                    if filename == "logcat.log":
                        report["result"] = "FAIL"
                        report.setdefault("error", str(failure))
            try:
                (root / "emulator-screen.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True, required=False))
                names = adb("shell", "run-as", PACKAGE, "ls", "files/integration-evidence", required=False).splitlines()
                for name in names:
                    if re.fullmatch(r"[a-zA-Z0-9_-]+\.png", name):
                        (root / name).write_bytes(adb("exec-out", "run-as", PACKAGE, "cat", "files/integration-evidence/" + name, binary=True))
            except Exception as failure:
                report["screenshots"] = str(failure)
        count = len(TESTS) + len(CHECKPOINT_BOUNDARIES) + 2 if report["result"] == "PASS" else 1
        suite = ET.Element("testsuite", name=CLASS, tests=str(count), failures="0", errors="0" if report["result"] == "PASS" else "1", skipped="0")
        if report["result"] == "PASS":
            for name in TESTS:
                ET.SubElement(suite, "testcase", classname=CLASS, name=name)
            ET.SubElement(suite, "testcase", classname=f"{PACKAGE}.MainActivity", name="coldLaunchWithoutInstrumentation")
            for boundary in report["checkpointInterruptions"]:
                ET.SubElement(suite, "testcase", classname=CHECKPOINT_CLASS, name=boundary)
            ET.SubElement(suite, "testcase", classname=CHECKPOINT_CLASS, name=COMPETE)
        else:
            case = ET.SubElement(suite, "testcase", classname=CLASS, name="integration_runner")
            ET.SubElement(case, "error", message=report.get("error", "Integration failed")).text = report.get("error")
        ET.ElementTree(suite).write(root / "junit.xml", encoding="utf-8", xml_declaration=True)
        (root / "commands.json").write_text(json.dumps(commands, indent=2), encoding="utf-8")
        (root / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["result"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
