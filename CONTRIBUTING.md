# Contributing to Grimoire

Thanks for helping out. This is the human-facing guide to building, testing, and
shipping changes. For the deep architecture map and the per-screen gotchas, read
[`CLAUDE.md`](./CLAUDE.md) — it's the orientation doc for the codebase (written
for AI agents, but just as useful to people). Treat the **code as the source of
truth**; if a doc drifts, fix the doc in the same PR.

## Prerequisites

- **JDK 17** (CI builds on Temurin 17)
- **Android SDK** — compileSdk API 36.1, minSdk 26, targetSdk 36
- Access to the **`io.grimoire:extensions-api`** dependency. It resolves from, in
  order, your local Maven cache then GitHub Packages:
  - **mavenLocal** — if you also work on `grimoire-extensions-api`, run
    `./gradlew :api:publishToMavenLocal` there and it's picked up automatically.
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
