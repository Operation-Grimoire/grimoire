# Android integration journeys

[Issue #448](https://github.com/Operation-Grimoire/grimoire/issues/448) adds an
API 35 emulator gate for the real `MainActivity`, Hilt graph, Room store and
reader UI. Local content keeps these tests independent of websites, extension
parsers, credentials and external services.

The maintained suite exercises six paths:

- Actual touch input, Back flush, cold Activity close/relaunch and configuration
  recreation restore the committed paragraph while networking is disabled.
- Restoring a portable End checkpoint does not mark a chapter read. Explicit Next
  marks only the departed chapter read through the guarded production facade,
  creating one intent/receipt without setting stored compatibility progress to one.
  Committed read proposals fill a null `firstReadAt` and preserve an existing time.
  Explicit MarkRead can fill it on an already-read row; Next is unchanged when
  already read, so that case does not create a timestamp.
- A real SQLite checkpoint-write fault reaches the reader's error dialog. Dismiss
  retains pending input; removing the fault and navigating Back retries the save.
- A real completion-write fault leaves the read flag unchanged. Retry commits one
  intent/receipt; explicit unread preserves first-read time across Activity reopen.
- A global downloaded-word-count backfill fault reports a committed read as partial.
  A later explicit unread persists, and retrying the old read receipt fills the word
  count without changing that unread or adding another completion intent/receipt.
- Scrolling to the end through layout alone leaves the chapter unread even after
  earlier user input. Consumed user scroll completes it. Explicit unread replaces
  visual authority, and fresh scroll can complete it again while preserving the
  original first-read time. Back retires the exact writer.

Fixture setup uses a debug-only Hilt entry point to existing bindings. It does not
replace providers or add a production test switch. The interface is in
`app/src/debug`, excluded from release builds; ordinary release source changes
still trigger normal publication policy. Its own debug/test/workflow changes run
CI without publishing a phone beta.

The debug host includes the same AndroidX test-core version already used by
instrumentation. ActivityScenario's helper activities then resolve in the host
alongside the host's existing monitor classes. Splitting those helpers into the test APK
while their `ActivityInvoker` interface is deduplicated into the host causes a
separate helper process to crash and delays each Activity close by about 45 seconds
(#456). This dependency and its activities are excluded from release builds.
Debug compilation pins monitor to the same 1.6.1 version already in the host
runtime, so trusted-base dependency preparation also covers the offline head build.

## CI and evidence

`.github/workflows/android-integration.yml` runs for Android/shared impact on pull
requests and master. A token-scoped step first builds the trusted base commit to
resolve published dependencies, without a persistent Gradle/Kotlin daemon or
configuration cache. The exact head then builds offline with both token environment
variables empty. Credentials are never stored in properties or passed to head code.
A dependency-pin change needs trusted-base dependency preparation; the build never
falls back to supplying a token to the head. The workflow
hashes the debug host/test APKs, then runs a fresh headless API 35 emulator. It
retains the existing extension-host workflow and its separate ART helper coverage.

The runner refuses non-emulator devices, clears only its disposable fixture app,
enables airplane mode and disables Wi-Fi/mobile data, then passes the source SHA into the test. The test checks
that SHA against `BuildConfig`, and independently verifies no active network.
Six named starts and six paired successful completions are mandatory. Skips,
assumptions, duplicates, missing cases, wrong classes/counts and aborted runs fail.
App/test-helper process crashes in logcat also fail, even if every named status
reports success; logcat capture must succeed and be nonempty. Cleanup failures
retain the original instrumentation failure alongside the logcat error.
The parser has deterministic negative tests in the Python policy job.

The `android-integration-apks` artifact binds host/test hashes. The results artifact
contains the named inventory/report, instrumentation log, JUnit XML, command log,
logcat, connectivity/Activity state and screenshots. Fixture failures capture the live screen
before cleanup; the runner also captures the emulator screen. A failed setup is a
failed setup, not successful app acceptance or a reason to silently rerun a test.

## Running and extending the suite

Use a disposable API 35 emulator, never a personal app/device. Build and run:

```sh
export APP_VERSION_TAG=v0.0.0 APP_BETA_NUMBER=1
export APP_GIT_SHA=$(git rev-parse HEAD)
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
mkdir -p build/android-integration
cp app/build/outputs/apk/debug/app-debug.apk build/android-integration/host.apk
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk build/android-integration/tests.apk
(cd build/android-integration && sha256sum host.apk tests.apk > sha256sums.txt)
INTEGRATION_SOURCE_SHA=$APP_GIT_SHA python .github/scripts/android_integration.py
```

Add a deterministic production journey to `ReaderIntegrationTest` and its exact
method name to `TESTS` in the runner. Keep website/parser assertions in the
extensions repository. Run the runner's Python tests and compile instrumentation
locally; require actual hosted emulator execution before claiming runtime proof.

These are debug-host emulator journeys. They do not establish physical-phone,
signed-release, true process-death, competing-process, live-source or desktop
acceptance. Activity recreation and Activity close/relaunch are labelled as such.
