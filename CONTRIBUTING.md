# Contributing to Grimoire

Thanks for helping out. This is the human-facing guide to building, testing, and
shipping changes. For the deep architecture map and the per-screen gotchas, read
[`CLAUDE.md`](./CLAUDE.md) — it's the orientation doc for the codebase (written
for AI agents, but just as useful to people). Treat the **code as the source of
truth**; if a doc drifts, fix the doc in the same PR.

## Prerequisites

- **JDK 17** (CI builds on Temurin 17)
- **Android SDK** — compileSdk API 36.1, minSdk 26, targetSdk 36
- Access to the **`io.grimoire:extensions-api`** Android wrapper and its transitive
  **`io.grimoire:extensions-core`** JVM dependency. API 1.5 also brings the
  `browser-sessions-android` and `browser-sessions-core` artifacts from the
  [Browser sessions repository](https://github.com/Operation-Grimoire/browser-sessions). They resolve from, in
  order, your local Maven cache then GitHub Packages:
  - **mavenLocal** — if you also work on `grimoire-extensions-api`, run
    `./gradlew :core:publishToMavenLocal :api:publishToMavenLocal` there. Select
    that version with `-PextensionsApiVersion=VERSION` if it differs from the
    app's stable pin. Both matching artifacts must be available.
  - **GitHub Packages** — otherwise export `GITHUB_ACTOR` (your username) and
    `GITHUB_TOKEN` (a PAT with `read:packages`) so Gradle can authenticate.
- Optional: a `GITHUB_OAUTH_CLIENT_ID` (gradle property or env) for your own
  OAuth app; not needed just to build.

## Build & test

```bash
./gradlew :app:compileDebugKotlin     # fast typecheck
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:lintDebug              # Android lint
./gradlew :app:installDebug           # install on a connected device
APP_VERSION_TAG=v0.1.0 ./gradlew :app:assembleRelease
```

Run at least `compileDebugKotlin` and `testDebugUnitTest` before opening a PR.
Pure projection/logic changes should come with a unit test (see
`LibraryFilterTest`, `ChapterProjectionTest` for the pattern).

For local API development, pass `-PgrimoireApiDir=/path/to/grimoire-extensions-api`
and `-PextensionsApiVersion=VERSION` to use the composite instead of published
packages. Normal builds use the stable release without either property.
The normal Android build resolves both GitHub Packages repositories with the same
package-read credentials.

### Extension API consumer regression

The `Extension host API` workflow builds the app with its normal published API
pin and five extension APKs from a fixed revision of `grimoire-extensions`.
It installs them on a fresh API 35 emulator and runs `ExtensionHostApiTest`.
The APKs use the existing extension source directories; no source implementation
is copied into the app or rewritten for the test.

The tests check host ownership of the cookie/browser APIs, host-written
fixture login cookies followed by a signed-in assertion and logout on all four login sources,
and a controlled rendered page through the API resolved by GalaxyNovels.
These checks do not establish real-site authentication or challenge handling.
The workflow retains APK hashes and the instrumentation result.

For a local run, use a disposable emulator. Build the host and test APKs:

```sh
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Build the five fixture APKs from the workflow's pinned extension revision.
Set `EXT` to that checkout and install its APKs:

```sh
bash "$EXT/gradlew" -p "$EXT" :en-webnovel:assembleDebug :en-azurechronicles:assembleDebug \
  :all-zlibrary:assembleDebug :en-caleredhair:assembleDebug :ar-galaxynovels:assembleDebug
for module in en/webnovel en/azurechronicles all/zlibrary en/caleredhair ar/galaxynovels; do
  artifact=${module//\//-}
  adb install -r -t "$EXT/src/$module/build/outputs/apk/debug/$artifact-debug.apk"
done
```

Then run the regression:

```sh
adb shell am instrument -w -r \
  -e class io.grimoire.app.extension.ExtensionHostApiTest \
  -e extensionApiFixtures true \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner
```

The fixture sources are WebNovel, Azure Chronicles, Z-Library, Cale Red Hair,
and GalaxyNovels. Without the explicit fixture flag, these tests are skipped.
With the flag, missing fixture packages fail the run. Use the workflow's pinned
source revision to reproduce its APKs. A successful run reports `OK (3 tests)`.

## Optional diagnostics configuration

Set `APTABASE_APP_KEY` through a Gradle property, environment variable, or
gitignored `local.properties` entry. Release and nightly workflows use the
repository variable of the same name. Both diagnostics integrations are inert
when the key is missing. The crash adapter supports hosted US/EU keys.

Settings → Privacy has independent crash-reporting and usage-analytics toggles.
Existing choices are preserved. Crash capture starts after the stored preference
loads; it never assumes consent while that read is pending. Unreadable or corrupt
preferences disable diagnostics instead of falling back to default-on consent.

Uncaught crashes still produce the local report shown on the next launch.
Separately, the Aptabase adapter saves an anonymous report synchronously and
uploads it on the next launch through WorkManager when a network is available.
Automatic reports contain exception types, stack-frame identifiers and line
numbers, capture time, app version/commit, Android version, a temporary session
ID, and debug/release mode. They omit exception messages, source filenames,
URLs, novel identifiers, device identifiers, and local-report breadcrumbs.

The upload journal is excluded from Android backups, holds at most eight reports,
and expires reports after 14 days. Opting out clears it synchronously and cancels
upload work before the asynchronous preference write completes.
Transient network/server failures and rate limiting retry with backoff; invalid
payloads, invalid keys and exhausted quotas are discarded. Debug reports retain
their original build mode even when uploaded by a later build.

The adapter follows Aptabase's [error ingestion contract](https://github.com/aptabase/aptabase/blob/main/docs/error-api-openapi.yaml).
Local HTTP tests validate delivery behavior. Before claiming live acceptance,
verify Errors availability in the configured Aptabase account, send a deliberate
debug crash, relaunch, and confirm that report in the dashboard's debug view.
HTTP 202 means buffered acceptance, not confirmed dashboard storage.

## Conventions (the short version)

The full rationale lives in [`CLAUDE.md`](./CLAUDE.md); the essentials:

- **MVI-lite per screen**: `XScreen` + `XViewModel`. ViewModels expose
  `StateFlow`; screens read via `collectAsState`.
- **Filter / sort / search projections belong in the ViewModel** — a pure
  top-level function backed by a debounced `combine(...).stateIn(...)`. Don't
  recompute projections in the composable body or in `derivedStateOf`.
- **Swipeable tabs everywhere**: use `ui/component/SwipeTabRow.kt`, not a
  hand-rolled `TabRow` that switches content via `when`.
- **Theme, not literals**: reach for `MaterialTheme.colorScheme.*` /
  `MaterialTheme.shapes.*`; no raw `Color.Black` / hex for surfaces and scrims.
- **Depend on the extension API, not concrete extensions.** App-side code uses
  `io.grimoire:extensions-api` interfaces only.
- **Don't enable R8** in a one-off PR without adding the Hilt/Room/serialization
  keep rules in the same commit.
- Delete dead composables you notice; don't leave unused `private fun`s.

See the "Known pre-existing warnings" list in `CLAUDE.md` — don't fix those in
unrelated PRs.

## Branching, commits & PRs

- Branch off `master`; never push directly to it.
- Keep PRs focused; update any doc your change contradicts in the same PR.
- Write clear commit messages — a one-line summary, then *why* the change is
  needed when it isn't obvious from the diff.
- CI builds signed release APKs from a pushed `vX.Y.Z` tag
  (`.github/workflows/release.yml`); the `versionCode`/`versionName` are derived
  from that tag, so you don't bump versions by hand.

## License

By contributing, you agree your contributions are licensed under the project's
[Apache License 2.0](./LICENSE).
