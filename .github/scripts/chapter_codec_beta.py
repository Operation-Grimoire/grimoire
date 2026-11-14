"""Signed, isolated CI-only DB32 -> DB33 upgrade; no reader-UI acceptance claim."""

import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import sys


OUTPUT = Path("build/chapter-codec-beta")
PACKAGE = "io.grimoire.app"
BASELINE = "v1.0.5-beta.342"
BASELINE_SHA256 = "7caae98d569f918715d83e15c90cef86655988114f4a7ae561f8ba4047383650"
SIGNER = "64af06095973e42f943c8bd9bdafb4cf168a33cd4d570b381c31e0524eb454de"
BODY = "Codec checkpoint first.\x1c<b>Codec checkpoint first.</b>\x1f\x1d\x1f\x1ehttps://fixture.invalid/i.png\x1fCodec checkpoint second.\x1fCodec checkpoint final."


def run(*args, check=True):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=60)
    with (OUTPUT / "commands.log").open("a", encoding="utf-8") as log:
        log.write("$ " + " ".join(map(str, args)) + "\n" + result.stdout + "\n")
    if check and result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {' '.join(map(str, args))}\n{result.stdout}")
    return result


def main():
    # This harness intentionally replaces a fixture app DB. Never run it on a
    # person's workstation or against an attached physical device.
    if not __debug__:
        raise RuntimeError("CI-only fixture harness: Python assertions must be enabled")
    workspace_value = os.environ.get("GITHUB_WORKSPACE")
    workspace = Path(workspace_value).resolve() if workspace_value else None
    if (sys.platform != "linux" or os.environ.get("GITHUB_ACTIONS") != "true" or
            os.environ.get("RUNNER_OS") != "Linux" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted" or
            workspace is None or workspace != Path.cwd().resolve() or
            not workspace.is_relative_to(Path("/home/runner/work").resolve())):
        raise RuntimeError("CI-only fixture harness; requires enabled assertions and the fresh hosted Linux workspace")
    tag = os.environ["CODEC_BETA_TAG"]
    assert re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+-beta\.[0-9]+", tag) and tag != BASELINE
    expected_sha = os.environ["GITHUB_SHA"]
    assert re.fullmatch(r"[0-9a-f]{40}", expected_sha)
    OUTPUT.mkdir(parents=True, exist_ok=False)  # Preserve any earlier attempt.
    report = {"result": "FAIL", "baseline": BASELINE, "target": tag, "commit": expected_sha,
              "scope": "Signed artifact, native startup, database migration, saved-content/progress preservation and downgrade refusal. Reader UI/new-download UI not exercised."}
    try:
        devices = run("adb", "devices").stdout.splitlines()[1:]
        serials = [line.split()[0] for line in devices if line.strip().endswith("\tdevice")]
        assert len(serials) == 1 and serials[0].startswith("emulator-"), "Require one isolated emulator"
        adb = ["adb", "-s", serials[0]]
        sdk = Path(os.environ["ANDROID_HOME"])
        tools = max((p for p in (sdk / "build-tools").iterdir() if re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", p.name)),
                    key=lambda p: tuple(map(int, p.name.split('.'))))
        repo = os.environ["GITHUB_REPOSITORY"]
        artifacts = {}
        for release_tag, directory in ((BASELINE, "baseline"), (tag, "target")):
            path = OUTPUT / directory
            path.mkdir()
            release = json.loads(run("gh", "api", f"repos/{repo}/releases/tags/{release_tag}").stdout)
            assert release["prerelease"] and not release["draft"]
            if directory == "target":
                assert release["target_commitish"] == expected_sha
            run("gh", "release", "download", release_tag, "--repo", repo, "--pattern", "grimoire.apk",
                "--pattern", "grimoire.apk.sha256", "--dir", str(path))
            apk = path / "grimoire.apk"
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            assert digest == (path / "grimoire.apk.sha256").read_text().strip()
            asset = next(a for a in release["assets"] if a["name"] == "grimoire.apk")
            assert asset["state"] == "uploaded" and asset["digest"] == "sha256:" + digest
            if directory == "baseline":
                assert digest == BASELINE_SHA256
            signature = run(str(tools / "apksigner"), "verify", "--print-certs", str(apk)).stdout
            assert re.search(r"certificate SHA-256 digest: ([a-f0-9]{64})", signature)[1] == SIGNER
            badging = run(str(tools / "aapt"), "dump", "badging", str(apk)).stdout
            assert "name='io.grimoire.app'" in badging and f"versionName='{release_tag[1:]}+" in badging
            if directory == "target":
                build = run(str(sdk / "cmdline-tools/latest/bin/apkanalyzer"), "dex", "code", "--class",
                            "io.grimoire.app.BuildConfig", str(apk)).stdout
                assert f'GIT_SHA:Ljava/lang/String; = "{expected_sha}"' in build
            artifacts[directory] = {"apk": str(apk), "sha256": digest}

        run(*adb, "root")
        run(*adb, "wait-for-device")
        run(*adb, "shell", "svc", "wifi", "disable")
        run(*adb, "shell", "svc", "data", "disable")
        run(*adb, "install", artifacts["baseline"]["apk"])
        app_path = "/data/user/0/" + PACKAGE
        db_path = app_path + "/databases/grimoire.db"
        uid = run(*adb, "shell", "stat", "-c", "%u", app_path).stdout.strip()
        assert uid.isdigit()
        fixture = OUTPUT / "fixture.db"
        with sqlite3.connect(fixture) as db:
            db.executescript(Path("app/src/androidTest/assets/chapter-codec/v32.sql").read_text())
            db.execute("""INSERT INTO novels (id,sourceId,url,title,genres,status,favorite,lastUpdated,
                chapterSortOrder,lastReadAt,lastAccessedAt,readerTextAlign,notifyOnNewChapters,
                notifyOnNewLockedChapters,autoDownloadNewChapters)
                VALUES (1,0,'local:codec409','Codec migration checkpoint','',0,1,0,0,0,0,0,0,0,0)""")
            db.execute("""INSERT INTO chapters (id,novelId,url,name,uploadDate,chapterNumber,translator,
                read,readProgress,readAnchorItemIndex,readAnchorItemOffset,downloadStatus,queueOrder,
                firstReadAt,wordCount,locked) VALUES (1,1,'local:codec409/0','Migration checkpoint',1234,1.5,'T',1,0.625,3,17,3,9,789,9,0)""")
            db.execute("INSERT INTO chapter_content (chapterId,content) VALUES (1,?)", (BODY,))
        run(*adb, "shell", "mkdir", "-p", app_path + "/databases")
        run(*adb, "push", str(fixture), db_path)
        run(*adb, "shell", "chown", f"{uid}:{uid}", app_path + "/databases", db_path)
        run(*adb, "shell", "restorecon", "-R", app_path + "/databases")

        def capture(label):
            run(*adb, "shell", "am", "force-stop", PACKAGE)
            path = OUTPUT / label
            path.mkdir()
            run(*adb, "pull", db_path, str(path / "grimoire.db"))
            if run(*adb, "shell", "test", "-f", db_path + "-wal", check=False).returncode == 0:
                run(*adb, "pull", db_path + "-wal", str(path / "grimoire.db-wal"))
            with sqlite3.connect(path / "grimoire.db") as db:
                version = db.execute("PRAGMA user_version").fetchone()[0]
                metadata = db.execute("SELECT * FROM chapters ORDER BY id").fetchall()
                body = db.execute("SELECT content FROM chapter_content WHERE chapterId=1").fetchone()[0]
                assert body.encode() == BODY.encode()
                format_version = db.execute("SELECT contentVersion FROM chapter_content WHERE chapterId=1").fetchone()[0] if version == 33 else 1
                assert format_version == 1
            return version, metadata

        def launch(label, expected_version, expected_metadata=None):
            run(*adb, "logcat", "-c")
            started = run(*adb, "shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity").stdout
            assert "Status: ok" in started
            # UiAutomator waits for the real app's startup to settle; XML is
            # retained as observation only, not a reader-journey oracle.
            run(*adb, "shell", "uiautomator", "dump", "/sdcard/codec.xml")
            run(*adb, "pull", "/sdcard/codec.xml", str(OUTPUT / (label + ".xml")))
            logs = run(*adb, "logcat", "-d").stdout
            (OUTPUT / (label + ".log")).write_text(logs)
            assert "Process: io.grimoire.app," not in logs, "Unexpected Grimoire crash"
            version, metadata = capture(label)
            assert version == expected_version, (label, version)
            if expected_metadata is not None:
                assert metadata == expected_metadata
            return metadata

        before = launch("before", 32)
        run(*adb, "install", "-r", artifacts["target"]["apk"])
        launch("upgraded", 33, before)
        launch("restart", 33, before)
        # Negative rollback check with the actual old signed APK. No data clear
        # or destructive fallback is allowed; reinstalling the new APK recovers.
        downgrade = run(*adb, "install", "-r", "-d", artifacts["baseline"]["apk"], check=False)
        if downgrade.returncode == 0:
            run(*adb, "logcat", "-c")
            run(*adb, "shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity", check=False)
            run(*adb, "shell", "uiautomator", "dump", "/sdcard/rollback.xml", check=False)
            logs = run(*adb, "logcat", "-d").stdout
            (OUTPUT / "rollback.log").write_text(logs)
            assert re.search(r"(?i)(migration|downgrade)[^\n]*33\D+32", logs), "Old Room did not reject DB33"
            report["rollbackGuard"] = "Old signed APK refuses DB33; expected negative migration failure recorded"
        else:
            assert "VERSION_DOWNGRADE" in downgrade.stdout
            report["rollbackGuard"] = "Android package manager refuses older APK before data access"
        version, metadata = capture("after-rollback")
        assert version == 33 and metadata == before
        run(*adb, "install", "-r", artifacts["target"]["apk"])
        launch("recovered", 33, before)
        report.update(result="PASS", artifacts=artifacts, schemaBefore=32, schemaAfter=33,
                      legacyBodyUnchanged=True, readingMetadataUnchanged=True,
                      legacyBodySha256=hashlib.sha256(BODY.encode()).hexdigest(), chapterMetadataBefore=before,
                      restart="PASS", recovery="PASS", readerUi="UNEXECUTED")
    finally:
        (OUTPUT / "report.json").write_text(json.dumps(report, indent=2))
        print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
