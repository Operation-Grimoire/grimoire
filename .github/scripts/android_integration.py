"""Run the owned-emulator production journeys and require every named test to execute."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

CLASS = "io.grimoire.app.integration.ReaderIntegrationTest"
TESTS = (
    "inputBackColdReopenAndRecreationRestoreOfflinePosition",
    "restoredEndDoesNotMarkReadUntilExplicitNextNavigation",
    "failedCheckpointSaveCanBeDismissedAndRetriedWithoutLosingInput",
)
PACKAGE = "io.grimoire.app"


def parse_instrumentation(output):
    """Reject skips, wrong inventory, duplicate completions and incomplete runner output."""
    expected = {(CLASS, name) for name in TESTS}
    started, succeeded, bundle = set(), set(), {}
    for line in output.splitlines():
        field = re.fullmatch(r"INSTRUMENTATION_STATUS: ([^=]+)=(.*)", line)
        if field:
            bundle[field[1]] = field[2]
        status = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line)
        if not status:
            continue
        code = int(status[1])
        case = (bundle.get("class"), bundle.get("test"))
        if case not in expected or code not in (0, 1):
            raise ValueError(f"Unexpected/failed/skipped instrumentation event: {case}, code {code}")
        if "numtests" in bundle and bundle["numtests"] != str(len(TESTS)):
            raise ValueError("Instrumentation inventory count differs")
        if code == 1:
            if case in started:
                raise ValueError(f"Duplicate test start: {case}")
            started.add(case)
        else:
            if case not in started or case in succeeded:
                raise ValueError(f"Unpaired or duplicate success: {case}")
            succeeded.add(case)
        bundle = {}
    if started != expected or succeeded != expected:
        raise ValueError(f"Incomplete executed inventory: started={started}, succeeded={succeeded}")
    if (not re.search(rf"^OK \({len(TESTS)} tests\)\s*$", output, re.M)
            or len(re.findall(r"^INSTRUMENTATION_CODE: -1\s*$", output, re.M)) != 1
            or re.search(r"FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|shortMsg=|Process crashed", output)):
        raise ValueError("Instrumentation did not complete successfully")
    return sorted(name for _, name in succeeded)


def main():
    root = Path("build/android-integration")
    root.mkdir(parents=True, exist_ok=True)
    commands = []
    owned = False
    report = {"result": "FAIL", "expectedClass": CLASS, "expectedTests": list(TESTS)}

    def adb(*args, timeout=90, required=True, binary=False):
        command = ["adb", *args]
        commands.append(command)
        try:
            result = subprocess.run(command, capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired as failure:
            if "instrument" in args:
                (root / "instrumentation.log").write_bytes(failure.stdout or b"")
            raise
        if "instrument" in args:
            (root / "instrumentation.log").write_bytes(result.stdout)
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
                     PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=240)
        (root / "instrumentation.log").write_text(output + "\n", encoding="utf-8")
        report["executedTests"] = parse_instrumentation(output)
        report["result"] = "PASS"
    except Exception as failure:
        report["error"] = str(failure)
    finally:
        if owned:
            for filename, args in [("logcat.log", ("logcat", "-d", "-v", "threadtime")),
                                   ("connectivity.log", ("shell", "dumpsys", "connectivity")),
                                   ("activity.log", ("shell", "dumpsys", "activity", "activities"))]:
                try:
                    (root / filename).write_text(adb(*args, required=False), encoding="utf-8")
                except Exception as failure:
                    report[filename] = str(failure)
            try:
                (root / "emulator-screen.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True, required=False))
                names = adb("shell", "run-as", PACKAGE, "ls", "files/integration-evidence", required=False).splitlines()
                for name in names:
                    if re.fullmatch(r"[a-zA-Z0-9_-]+\.png", name):
                        (root / name).write_bytes(adb("exec-out", "run-as", PACKAGE, "cat", "files/integration-evidence/" + name, binary=True))
            except Exception as failure:
                report["screenshots"] = str(failure)
        suite = ET.Element("testsuite", name=CLASS, tests=str(len(TESTS) if report["result"] == "PASS" else 1), failures="0", errors="0" if report["result"] == "PASS" else "1", skipped="0")
        if report["result"] == "PASS":
            for name in TESTS:
                ET.SubElement(suite, "testcase", classname=CLASS, name=name)
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
