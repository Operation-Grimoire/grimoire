# CLAUDE.md — Agent guide for the Grimoire app

This file is the orientation a Claude agent should read before editing the Grimoire app. It complements [`README.md`](./README.md) (public-facing intro) with the gotchas, conventions, and "where do I find X" that aren't obvious from the source tree.

If something here drifts from the code, **trust the code and update this file**.

## Tech baseline

- **Kotlin 2.2.10** / KSP 2.2.10-2.0.2 / AGP 9.2.1
- **Compose BOM 2026.02.01**, Material 3
- **minSdk 26**, **targetSdk 36** (compileSdk API 36.1), Java 11 / jvmTarget 11
- Hilt 2.59.2, Room 2.7.1, Navigation-Compose 2.8.9, Coil 2.7, OkHttp 4.12, DataStore 1.1, WorkManager 2.10
- `androidx.media` 1.7 is the legacy `MediaSessionCompat` — TTS still uses it; do **not** introduce media3 in a single screen without migrating the whole TTS playback service at once
- `biometric:1.2.0-alpha05` + `security-crypto:1.1.0-beta01` are intentionally alpha/beta; watch for breakage on bumps
- Release builds run **without R8** (`isMinifyEnabled = false`). Don't enable minify in a one-off PR without adding the Hilt / Room / serialization keep rules

## Package layout — io.grimoire.app

```
ui/                  Compose UI (Scaffold, NavHost, screens, ViewModels)
  AppNavigation.kt     Top-level Scaffold + bottom nav + NavHost
  AppNavGraphs.kt      Per-feature NavGraphBuilder extensions
  AppRoutes.kt         Route constants + TopLevelDestination
  component/           Shared composables
  screen/              browse / downloads / extensions / library / migrate /
                       more / novelupdates / reader / settings (15 subscreens) /
                       tasks / updates / webview
  theme/, update/
data/
  local/               Room: AppDatabase, 6 DAOs, 7 entities
  preferences/         14 DataStore-backed pref classes + PreferenceStore
  source/              ChapterListFetcher + paginated-source helpers
  download/            DownloadManager / Service / ChapterImageStore
  libraryupdate/       Scheduler / Worker / Updater
  backup/              BackupManager / Scheduler / Worker / Models
  tts/                 13 files — DeviceTts + ElevenLabs + PlaybackManager / Service / Notification
  novelupdates/        NU.com scraper (Client / Parser / Matcher / Endpoints / Models)
  update/              In-app GitHub release updater + Changelog parser
  epub/                EpubImporter / Parser, Local source
  cache/               CoverPreloader
domain/                auth / migration / novelupdates info repositories
extension/             ExtensionLoader / Manager + repo browsing
auth/github/           OAuth device flow (5 files)
di/                    Hilt modules: Database, Preferences, GitHubAuth
util/                  ContentLanguages, LanguageLabels
```

## Architecture conventions

- **MVI-lite per screen**: `XScreen` + `XViewModel` per feature. ViewModels expose `StateFlow`, screens read via `collectAsState`.
- **Filter / sort / search projections belong in the ViewModel.** Use a pure top-level function (`computeTabNovels` in `screen/library/LibraryFilter.kt`, `projectChapters` in `screen/browse/ChapterProjection.kt`) and back it with a derived `StateFlow` via `combine(...).flowOn(Dispatchers.Default).stateIn(...)`. Don't recompute the projection in the screen body on every recomposition.
- **Search input is debounced 120 ms at the VM** (`MutableStateFlow<String>.debounce(120L)`) so each keystroke doesn't refire the projection.
- **Pager pages read precomputed tabs** by index. Never run the per-page filter inside `HorizontalPager`'s page lambda — that triples the work on adjacent-page preload.
- **Hidden categories / locked chapters**: the *locked* visibility state hides hidden-category novels from the All tab regardless of `includeHiddenInAll`; the *unlocked* state honours the pref. Tests in `LibraryFilterTest` cover both.
- **EPUB intent**: the `pendingEpubUri: StateFlow<Uri?>` is plumbed through `AppNavigation` → `libraryDestination` → `LibraryScreen` so the import-preview dialog renders inside the Library tab.

### When to extract code from a screen

When `XScreen.kt` crosses ~800 LOC, lift each file-private composable into its own file in the same package and mark it `internal`. The convention used through `library/`, `browse/`, `reader/` is:

- One sheet per file (`FilterSortSheet.kt`, `RefreshSummaryDialog.kt`, `ReaderSettingsSheet.kt`, `ManageCategoriesSheet.kt`)
- One row / card per file when they have non-trivial state (`NovelCard.kt`, `NovelRow.kt`, `NovelStatsRow.kt`)
- Pure projection functions go alongside as `internal fun ...` with their own `*Test` (see `LibraryFilterTest`, `ChapterProjectionTest`).

Keep the same package — the screen's main composable still calls the moved helpers without imports, and Hilt scopes don't change.

## Navigation

`AppNavigation.kt` collapses to the Scaffold + `NavHost` call. The graph body delegates to `NavGraphBuilder` extension functions in `AppNavGraphs.kt`:

| Extension | Routes covered |
|-----------|----------------|
| `libraryDestination` | Library tab (consumes EPUB intent) |
| `browseGraph` | Browse home, global search, NovelUpdates browser / search / series |
| `moreDestinations` | More, Downloads, Statistics, Tasks, Library updates, Update issues, About |
| `settingsGraph` | 15 settings sub-screens, all sharing the `SettingsViewModel` via `getBackStackEntry(SETTINGS_GRAPH_ROUTE)` |
| `sourceDestinations` | Extensions, Source browse, Source settings, Source languages, Source login |
| `novelDetailDestinations` | Novel detail + migrate |
| `readerDestinations` | Reader + in-app WebView |

All route constants live in `AppRoutes.kt`. The externally-visible `NAV_TARGET_UPDATES` is the entry-point a notification can pass through `pendingTarget` to land on the Updates screen.

## Build / test

```bash
./gradlew :app:compileDebugKotlin            # fast typecheck
./gradlew :app:testDebugUnitTest             # JVM tests
./gradlew :app:installDebug                  # install on connected device
./gradlew :app:lintDebug                     # Android lint
APP_VERSION_TAG=v0.1.0 ./gradlew :app:assembleRelease
```

CI lives in `.github/workflows/release.yml` and builds signed release APKs from a git tag.

## Known pre-existing warnings (don't fix in unrelated PRs)

- `NovelDetailViewModel.kt` flatMapLatest needs `@OptIn(ExperimentalCoroutinesApi::class)` — pre-existing
- `AppUpdateUi.kt` uses `Icons.Filled.List` instead of `AutoMirrored.Filled.List`
- `AppUpdateChecker.kt` + `AppUpdateViewModel.kt` hit the Kotlin 2.2 annotation-target migration warning (KT-73255)
- `androidx.media` MediaSessionCompat deprecation in the TTS service (whole-service migration to media3 is its own PR)
- `SourceBrowseScreen.kt` uses deprecated `MenuAnchorType` typealias (renamed to `ExposedDropdownMenuAnchorType`)
- ChapterEntity Room query mismatch warnings (`downloadedContent` not returned by some queries)

## Don'ts

- **Don't add new screen-local `derivedStateOf { recompute(...) }` blocks** for filter / sort / search. Move it to the VM with a debounced `combine`.
- **Don't use raw `Color.Black` / hex literals** for surfaces / scrims. Reach for `MaterialTheme.colorScheme.scrim` and `MaterialTheme.shapes.*` instead.
- **Don't keep dead composables**. If a `private fun` is never called, delete it as part of whatever PR notices.
- **Don't enable R8** in a refactor PR without adding the keep rules in the same commit.
- **Don't bypass the extension API**. App-side code should depend on `io.grimoire:extensions-api` interfaces, not concrete extension classes (the app never loads extension code into its own classloader).

## Quick file map

| Looking for | File |
|-------------|------|
| Top-level navigation | `ui/AppNavigation.kt`, `ui/AppNavGraphs.kt`, `ui/AppRoutes.kt` |
| Library projection (pure) | `ui/screen/library/LibraryFilter.kt` + `LibraryFilterTest` |
| Chapter projection (pure) | `ui/screen/browse/ChapterProjection.kt` + `ChapterProjectionTest` |
| Room schema | `data/local/AppDatabase.kt`, `data/local/entity/*` |
| Preferences | `data/preferences/*.kt` (one class per logical group) |
| Background library refresh | `data/libraryupdate/*` |
| Backup format | `data/backup/BackupModels.kt` |
| TTS playback | `data/tts/TtsPlaybackManager.kt` + `TtsPlaybackService.kt` |
| GitHub OAuth | `auth/github/*` and `di/GitHubAuthModule.kt` |
| Extension discovery | `extension/ExtensionLoader.kt`, `extension/ExtensionManager.kt` |
