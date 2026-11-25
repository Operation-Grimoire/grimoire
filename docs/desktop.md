# Desktop reading slice

The maintained JVM target is `:desktop`. It reads a trusted local extension JAR,
stores a local library and paragraph position, and saves chapter text for offline
reading. Android and desktop use `:reading-core` for API compatibility, versioned
chapter encoding, saved-chapter reads and library membership contracts/repositories. Each platform
selects its backend adapter; see [shared storage](shared-storage.md). Both package
formats use the extension repository's existing source;
the application does not compile or copy a Royal Road implementation.

## Shared UI

`:shared-ui` is a Kotlin Multiplatform module with Android and desktop JVM targets.
It holds Compose UI that both apps render: the color palettes and typography, the
Material Symbols icons (`AppIcons`) and the theme controls. Composables take plain
state and callbacks; Android ViewModels and the desktop `ReaderModel` stay in their
apps. Strings used by shared composables are Compose Multiplatform resources in
`shared-ui/src/commonMain/composeResources`, with the same 18 translations as the
Android app. `LocaleCoverageTest` checks both sets. Move a screen's composables here
before giving Desktop its own version of that screen.

## Build and run

Use JDK 17 and the package-read credentials described in [CONTRIBUTING](../CONTRIBUTING.md).
Building the external extension JAR uses that repository's JDK 21 toolchain;
the application build and runtime use JDK 17. CI installs both explicitly.
The catalog pins immutable `extensions-core` 1.6.0, including cursor pagination.
CI and app publication use that same catalog version. Local API development can
select another version with `-PextensionsApiVersion=VERSION` or a local composite
with `-PgrimoireApiDir`; release builds require a concrete published version.

```sh
bash ./gradlew :reading-core:test :desktop:test
bash ./gradlew :desktop:run
```

On Windows, use `./gradlew.bat` instead of `bash ./gradlew`. The window opens in
**Library**. From the sidebar, choose **Extensions**. Desktop downloads the signed
JAR index of the official repository and lists its extensions. Choose **Install**,
then **Browse** to open the source's catalog. Browse shows the listings the source
offers: **Popular**, **Latest** and **Search**. Search runs without source filters.
**Load more** fetches the next page until a page adds no new titles. Installed
extensions load from the local store at startup, also offline. **Update** and **Remove** are on the same screen.
To add another repository, enter its index URL and the public key from its publisher,
check the fingerprint Desktop shows, then choose **Trust repository**. See
[desktop extension distribution](desktop-extensions.md) for the trust and recovery rules.

For development, **Open local extension** loads a trusted local manifest for the
current session only.
Open a novel, add it to the library, and choose an unlocked chapter. **Save chapter
offline** stores its text and adds the novel to the library. Scroll position is
saved automatically and flushed on navigation and normal shutdown.
Removing library membership retains downloaded chapters and reading progress,
matching Android's behavior; it does not delete saved content. Dedicated download
management remains later work.

By default, data is stored in `%APPDATA%/GrimoireDesktop` on Windows, or
`~/GrimoireDesktop` elsewhere. For an isolated run:

```sh
bash ./gradlew :desktop:run -PdesktopManifest=desktop/build/royalroad.properties \
  -PdesktopData=desktop/build/manual-library
```

To check offline reading, close the window, restart without selecting an
extension, enable **Offline**, open the saved novel and choose **Continue reading**.
Downloaded content loads without a source instance or network access. Offline
mode also rejects host HTTP requests. A missing or damaged download reports an
error; it does not silently fetch replacement content.

## Trusted local artifacts

This section covers development manifests. Installed extensions use the signed
repository flow above instead.

A local manifest does not establish remote authenticity. Extension code runs
with the application's permissions, installed or local. Only select JARs built from a
trusted checkout or authenticated through a separately trusted delivery channel.
The local manifest binds the selected bytes and metadata; computing a hash of an
untrusted download does not make it trusted.

Build the existing extension source, for example at the CI pin
`69b008be343e8cbec9287ef57f8505430a648494`:

```sh
bash ./gradlew -p /path/to/grimoire-extensions/jvm jarEnRoyalroad
python desktop/tools/local_manifest.py \
  --jar /path/to/grimoire-extensions/jvm/build/extensions/en-royalroad/io.grimoire.extension.en.royalroad-1.0.4.jar \
  --output desktop/build/royalroad.properties
```

The loader validates the SHA-256, package, entry point, independent artifact/source
versions, minimum API and required host libraries before initializing source code.
It requires modern JAR metadata; Android's legacy-APK exception does not apply to
JARs. API requirements use `major.minor`, requiring an equal major and sufficient
host minor. It rejects foreign class namespaces and external manifest classpaths,
and loads verified snapshots rather than mutable original paths. No loader cache
is reused between imports. Loaders close on replacement and shutdown.

`org.json:json:20240303` is provided by the host. Sources requiring the shared
extensions library also need its trusted JVM JAR passed with `--library` when
generating the manifest. Its hash and required coordinate are checked too. The
desktop host currently provides public HTTP only: login cookies and rendered-page
browsing are unavailable capabilities.

## Verification and limits

App behavior tests build their own small test-only extension JAR:

```sh
bash ./gradlew :desktop:integrationTest :desktop:uiTest
```

Compose tasks (`uiTest`, `extensionCompatibilityTest` and `liveSmoke`) require a
display. On headless Linux, install Xvfb and prefix each UI command with
`xvfb-run -a`, including the compatibility and live smoke commands below:

```sh
xvfb-run -a bash ./gradlew :desktop:integrationTest :desktop:uiTest
```

These tasks need no extensions checkout or external manifest. The fixture in
`desktop/src/readerFixture` returns fixed API data, with no website parser.
It is packaged separately and loaded by the production JAR loader; its classes
are absent from the host/test runtime classpath. A controlled HTTP platform records
calls and rejects unmatched requests before network access.

`ReaderJourneyTest` covers extension reimport/stale selection, duplicate-operation
rejection, locked chapters, saved content/progress and offline restart without an
extension. `ReaderUiTest` operates Compose controls and checks capture/save/offline
reopen, with images under `desktop/build/ui-evidence/`. Neither test owns real-source
selectors or parsing expectations. Both tasks always execute rather than reuse
cached test results.

A separate real packaged-extension compatibility smoke uses Royal Road and the
fixture responses maintained in the trusted extensions checkout:

```sh
bash ./gradlew :desktop:extensionCompatibilityTest \
  -PdesktopFixturesRepo=/path/to/grimoire-extensions \
  -PdesktopTestManifest=desktop/build/royalroad.properties
```

This checks that a real extension can run through the app controls and reopen saved
content offline. Source-specific parsing assertions belong to the extensions repo.
Its Compose evidence is under `desktop/build/compatibility-evidence/`. Missing
external inputs fail this task, without becoming prerequisites of the app tests.
Windows/Linux CI runs app tests first, then builds the pinned external JAR and runs
the compatibility smoke as a separate step. Storage/shared tests also run on macOS.

The coordinated snapshot overrides have been removed. These checks and nightly
publication resolve immutable API `1.6.0` from the catalog; development snapshot
results are separate evidence from validation against the published release.

`:desktop:liveSmoke` is a separate, explicit opt-in using the compatibility inputs.
It operates the Compose reader against the real public Royal Road catalog, reads
the first unlocked chapter, then saves and reopens it offline without a source
instance. It uses no accounts, cookies or rendered-page browser and is never part
of normal CI/check. Preserve its first result and images under
`desktop/build/live-evidence/`; fixture success does not establish live-site success.
Native window chrome, file dialogs, OS lifecycle and cross-OS runtime acceptance
remain separate gates.

The initial reader displays paragraph text and scene breaks. Rich HTML is retained
in downloads, but formatting and illustrations are not rendered yet. The local
library format is versioned separately from Android's Room database. Source ID plus
the source's novel and chapter URLs identify records; local row IDs are not sync
identities. URL migration and sync conflict policy remain future work.
New writes use the versioned shared codec delivered in
[#413](https://github.com/Operation-Grimoire/grimoire/pull/413); existing legacy
downloads retain their original bytes and interpretation. See
[chapter content](chapter-content.md) for format and rollback details.

Track runtime evidence and remaining acceptance in [#407](https://github.com/Operation-Grimoire/grimoire/issues/407).
Authenticated/browser-dependent sources, browser lifecycle work, macOS/Linux UI
acceptance, installers, signing, extension indexes/updates and accounts/sync remain
later [#397](https://github.com/Operation-Grimoire/grimoire/issues/397) gates. Stable
app/API/library releases require an explicit user request. Validate Android changes
on the exact signed nightly beta after merge.

Desktop storage now uses SQLite via JDBC behind the shared DAO contracts. See
[desktop storage and upgrade safety](desktop-storage.md) before opening an existing
data directory. Close older readers and retain a full pre-upgrade copy for downgrade.
The desktop workflow also runs storage and process-recovery tests on macOS; this
does not establish macOS UI or installer acceptance.
