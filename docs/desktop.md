# Desktop reading slice

The maintained JVM target is `:desktop`. It reads a trusted local Royal Road JAR,
stores a local library and paragraph position, and saves chapter text for offline
reading. Android and desktop use `:reading-core` for API compatibility and the
existing chapter-content encoding. Android storage and legacy APK behavior remain
unchanged. Both package formats use the extension repository's existing source;
the application does not compile or copy a Royal Road implementation.

## Build and run

Use JDK 17 and the package-read credentials described in [CONTRIBUTING](../CONTRIBUTING.md).
The normal build uses published `extensions-core` 1.5.0. No API composite or
prototype directory is required.

```sh
bash ./gradlew :reading-core:test :desktop:test
bash ./gradlew :desktop:run
```

On Windows, use `./gradlew.bat` instead of `bash ./gradlew`. The window opens your
local library. Choose **Open extension** to select a local manifest, then **Browse**.
Open a novel, add it to the library, and choose an unlocked chapter. **Save chapter
offline** stores its text and adds the novel to the library. Scroll position is
saved automatically and flushed on navigation and normal shutdown.

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

This slice does not download plugins or establish remote authenticity. Extension
code runs with the application's permissions. Only select JARs built from a
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

```sh
bash ./gradlew :desktop:integrationTest \
  -PdesktopFixturesRepo=/path/to/grimoire-extensions \
  -PdesktopTestManifest=desktop/build/royalroad.properties
```

The integration test loads the packaged Royal Road JAR and reuses its maintained
fixture contract's HTTP responses from the trusted checkout. It exercises the
application's browse/details/chapter path, rejects locked chapters before HTTP,
saves rich content and progress, then closes the source and restarts without an
extension. Any network request after that restart fails the test. Integration
tests always execute; they are not reused from a build cache. The desktop workflow
runs these checks and a Compose UI fixture journey on Windows and Linux. Run the
UI journey locally by replacing `:desktop:integrationTest` above with `:desktop:uiTest`.
It operates the reader controls and captures images under `desktop/build/ui-evidence/`.
These are controlled fixture checks, not
live-source or cross-OS UI acceptance.

`:desktop:liveSmoke` is a separate, explicit opt-in using the same arguments. It
operates the Compose reader against the real public Royal Road catalog, reads the
first unlocked chapter of the first listed novel, then saves and reopens it offline
without a source instance. It uses no accounts, cookies or rendered-page browser.
It is never included in normal CI/check. Preserve its first result and images in
`desktop/build/live-evidence/`; a blocked/failed live request must not be reported as
fixture acceptance. This harness exercises application composition and controls;
native window chrome, file dialogs and OS lifecycle still need separate acceptance.

The initial reader displays paragraph text and scene breaks. Rich HTML is retained
in downloads, but formatting and illustrations are not rendered yet. The local
library format is versioned separately from Android's Room database. Source ID plus
the source's novel and chapter URLs identify records; local row IDs are not sync
identities. URL migration and sync conflict policy remain future work.

Track runtime evidence and remaining acceptance in [#407](https://github.com/Operation-Grimoire/grimoire/issues/407).
Authenticated/browser-dependent sources, browser lifecycle work, macOS/Linux UI
acceptance, installers, signing, extension indexes/updates and accounts/sync remain
later [#397](https://github.com/Operation-Grimoire/grimoire/issues/397) gates. Stable
app/API/library releases require an explicit user request. Validate Android changes
on the exact signed nightly beta after merge.
