# Platform builds and release notes

Android (`app`), desktop (`desktop`), and `reading-core` stay in this repository.
Extensions keep one implementation, packaged as APKs or JARs in the extensions
repository. Independent application releases do not require duplicate sources.

## CI and publication

| Changed files | CI | Automatic publication |
| --- | --- | --- |
| Android runtime, resources or build | Android | Android beta |
| Desktop runtime or build | Desktop | None until installer acceptance |
| Shared runtime, root Gradle inputs or unknown modules | Both | Android beta |
| Platform tests | That platform | None |
| Workflows or release tooling | Relevant checks | None |
| Documentation or changelog fragments alone | Release policy | None |

The reusable `platform-impact.yml` workflow always validates the release policy.
Platform jobs use its outputs rather than workflow path filters, so a skipped
platform remains visible. A skipped job is not runtime acceptance evidence.

Android nightly publication compares the selected commit with the last complete
published Android release on its history. A desktop-only merge cannot lose an
Android change left unpublished by an earlier failed or cancelled build.
The planner checks paginated release metadata, tags, ancestry and expected assets
with an uploaded state and nonzero size;
an incomplete beta upload does not advance this boundary. Nightly dispatch has an
explicit `force` input for rebuilding unchanged Android code when needed.

## Add a changelog fragment

Runtime changes require a new `.changes/NUMBER-short-description.json` file:

```json
{
  "android": [
    {"category": "fix", "text": "Updates stay on the Android release channel."}
  ],
  "desktop": [
    {"category": "fix", "text": "The supplied extension opens when the app launches."}
  ]
}
```

Use `feature`, `fix`, `source`, `docs`, or `other`. Write one concise line per
entry. Both platform arrays are required. Use an empty array for a platform with
no user-facing change; two empty arrays explicitly classify an internal change.
CI-only, test-only and documentation-only changes do not require a fragment.

Fragments become immutable after merge. Add a correction in a new fragment;
do not edit, delete or consume old fragments when one platform publishes.
Different Android and desktop summaries can describe the same shared change.

Notes contain only that platform's entries added since its previous matching
published release. Beta ranges include the latest shipped stable or beta;
stable ranges start at the previous stable and include changes already tested
in betas. Empty notes remain empty, without build metadata or empty version
sections that would create a meaningless phone changelog dialog.

This policy starts with #410. Earlier release bodies remain available unchanged;
the new renderer does not reconstruct historical notes from repo-wide PR titles.
The first stable release using fragments therefore needs a review of any Android
changes predating this boundary before the user authorizes stable publication.

## Tags and update compatibility

Android retains `vX.Y.Z` and `vX.Y.Z-beta.N`. Keep the existing `nightly.yml`
workflow identity and `github.run_number` counter. Android's existing versionCode
encoding and its beta-number cap remain unchanged in this migration.

Desktop reserves `desktop-vX.Y.Z` and `desktop-vX.Y.Z-beta.N`, with one version
for all operating-system artifacts. Desktop CI writes a release-plan and scoped
notes artifact on master. It never publishes installers, including with force.
Installer acceptance and independent desktop version allocation are follow-up work.

The repository-wide GitHub latest slot remains Android's: Android stable releases
explicitly use `make_latest: true`; betas and future desktop publications must
use `make_latest: false` for older installed phones. New Android clients scan the
paginated feed and reject desktop/unrelated tags before selecting updates or notes.
They accept the expected `grimoire.apk` asset and retain legacy checksum support.
The shared feed scan is bounded at ten pages; exhaustion is an error, not an
up-to-date result.

Merging Android changes produces a signed beta. Validate that exact beta before
proposing stable. Create stable tags or dispatch `release.yml` only when the user
explicitly requests a stable release.

## Local verification

```sh
python -m unittest discover -s .github/scripts -p 'test_*.py' -v
python .github/scripts/release_policy.py validate --base origin/master --head HEAD
python .github/scripts/release_policy.py notes --platform android --base v1.0.5 --head HEAD --output build/android-notes.md
bash ./gradlew :desktop:help -I .github/scripts/verify-desktop-launch.gradle -PdesktopManifest=desktop/build/royalroad.properties '-PdesktopData=desktop/build/launch check' --no-configuration-cache
```

The Git-based commands inspect committed content. The launch check inspects the
actual final Compose run task; it does not start or stop the user's app window.
Use [the desktop guide](desktop.md) for trusted fixture manifests, runtime checks,
and the separate native window lifecycle acceptance boundary.
