# Android integration journeys

[Issue #448](https://github.com/Operation-Grimoire/grimoire/issues/448) adds an
API 35 emulator gate for the real `MainActivity`, Hilt graph, Room store and
reader UI. Local content keeps these tests independent of websites, extension
parsers, credentials and external services.

The maintained reader UI suite covers these behaviors.

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
- Novel Detail mark-all and selected-chapter actions, Library selection and
  Updates selection go through the guarded completion commands.
- Private and failed chapter opens respect the shared history queue and clear ordering.

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
(#456). This dependency and its activities are excluded from release builds;
`:app:verifyNoTestDependenciesInRelease` fails `testDebugUnitTest` and `assembleRelease` if any
`androidx.test` or `junit` dependency leaks into `releaseRuntimeClasspath`.
Debug compilation pins monitor to the same 1.6.1 version already in the host
runtime, so trusted-base dependency preparation also covers the offline head build.

## CI and evidence

`.github/workflows/android-integration.yml` runs for Android/shared impact on pull
requests and master. A token-scoped step first builds the trusted base commit to
resolve authenticated GitHub Packages dependencies, without a persistent
Gradle/Kotlin daemon or configuration cache. The exact head then builds with both
token environment variables empty, reusing the cached GitHub Packages artifacts
while still allowing public Gradle plugins and dependencies to resolve. Credentials
are never stored in properties or passed to head code. A GitHub Packages pin change
still needs trusted-base dependency preparation; the build never falls back to
supplying a token to the head. The workflow
hashes the debug host/test APKs, then runs a fresh headless API 35 emulator. It
retains the existing extension-host workflow and its separate ART helper coverage.

The runner refuses non-emulator devices, clears only its disposable fixture app,
enables airplane mode and disables Wi-Fi/mobile data, then passes the source SHA into the test. The test checks
that SHA against `BuildConfig`, and independently verifies no active network.
All named UI starts and paired successful completions are mandatory. Skips,
assumptions, duplicates, missing cases, wrong classes/counts and aborted runs fail.
An unexpected app/test-helper crash in logcat fails the gate even if every test
reports success. After `ReaderIntegrationTest`, the runner also force-stops the
package and launches `MainActivity` once via plain `am start -W` (without
instrumentation) to verify the process stays up and logcat stays crash-free when
`InstrumentationRegistry` is absent. Logcat capture must succeed and be nonempty. Cleanup failures
retain the original instrumentation failure alongside the logcat error.
The parser has deterministic negative tests in the Python policy job.

The `android-integration-apks` artifact binds host/test hashes. The results artifact
contains the named inventory/report, instrumentation log, JUnit XML, command log,
logcat, connectivity/Activity state and screenshots. Fixture failures capture the live screen
before cleanup; the runner also captures the emulator screen. A failed setup is a
failed setup remains visible in the results.

## Checkpoint process death

The emulator runner covers three process-death boundaries from
[#472](https://github.com/Operation-Grimoire/grimoire/issues/472) and
[#474](https://github.com/Operation-Grimoire/grimoire/issues/474). `CheckpointProcessDeathTest`
uses a fresh WAL-backed Room database for each scenario. A real
`ReaderCheckpoints` coordinator commits a baseline position and a neighbor
chapter position. The next capture targets a different block.

| Interruption | Recovery on the same database |
| --- | --- |
| After viewport capture while the writer is waiting to flush | The last committed position survives. The pending scroll was held in memory. |
| After the candidate write inside an open Room transaction | The last committed position survives. |
| After the DAO commits while the coordinator awaits its result | The new position survives even though the caller received no acknowledgement. |

The pending-scroll case submits a new capture to the production
`ReaderProgressWriter` with its 500 ms quiet period. The main thread checks
the pending queue before the delayed continuation can run and records that
the write callback has not started. This tests the in-memory loss window
without relying on timing an emulator swipe. The before-commit case nests the
production DAO call in an instrumented Room transaction so the test can
observe the candidate row before commit. The before-acknowledgement case uses
the DAO's normal transaction. Each writes a phase marker and
sends SIGKILL to its own instrumentation process. The runner requires the
expected exit, confirms the process is gone, then runs successful assertions
under a different PID. A fixture ID and source SHA bind both phase markers to
the same case. The new process verifies SQLite integrity, the exact target
restore point, the neighbor checkpoint, saved bodies, legacy progress and
completion fields, and opened-history rows.

The host archives each interruption and clean reopening as separate
instrumentation logs and phase markers. The three outcomes appear in JUnit
and the main report. A captured position can be lost until it commits.
Explicit Back waits for a flush, while a forced exit can bypass lifecycle
flushes. Signed reader UI behavior under interruption and physical-device tests
remain in [#435](https://github.com/Operation-Grimoire/grimoire/issues/435).

## Competing checkpoint writers

[#476](https://github.com/Operation-Grimoire/grimoire/issues/476) keeps the
instrumented app process and a private second process alive with separate Room
connections to the same WAL-backed fixture database. The second process uses a
debug-only provider that checks the caller UID and opens only the isolated
fixture. The release manifest excludes this component.

The first process commits block 3 and retains a later command with sequence 2.
The second process registers the same chapter and commits block 1 with its
own sequence 1. The original observation is still current, but its session
has been replaced. Room rejects the first process's delayed command as stale.
Both live processes read the same winning checkpoint, and a replay on the
neighboring chapter keeps its original position and authority. The test also
compares the novel and chapter rows, saved content and opened history.

The runner requires a successful instrumentation result and an evidence record
with both distinct PIDs, the fixture ID, exact block indices and sequences, the
stale rejection, and the source SHA. It archives the record and raw log with
the APK hashes. This exercises competing checkpoint authority through the
production adapter on the debug emulator. [#435](https://github.com/Operation-Grimoire/grimoire/issues/435)

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

The UI journeys cover Activity recreation and Activity close/relaunch on the
debug host. The process-death cases exercise the production checkpoint adapter
and coordinator. Signed beta reader UI restoration after a confirmed save was
verified separately in [#471](https://github.com/Operation-Grimoire/grimoire/issues/471).
