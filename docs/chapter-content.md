# Saved chapter format and migration

Android and desktop use the same codec in `reading-core`. Extensions still return
`NovelPage` values from one implementation; the hosts own persistence.

## Format versions

`StoredChapterContent` pairs a payload string with an explicit integer version.
The reader never infers a format from a prefix inside the source text.

| Version | Representation | Writes |
| --- | --- | --- |
| 1 | Existing US-separated tokens, with RS/image, GS/scene-break and FS/HTML markers | Legacy data only |
| 2 | JSON array of tagged page arrays | All new saves/imports |

For example, version 2 stores:

```json
[["text","Plain prose",null],["text","Rich prose","<i>Rich prose</i>"],["image","https://example.org/i.png"],["separator"]]
```

JSON escaping protects control characters in text, HTML and image URLs across
SQLite, UTF-8 and desktop-file round trips. A missing HTML value (`null`) stays
distinct from empty HTML (`""`). An empty chapter is `[]`; a single empty text
page is `[["text","",null]]`. Page indexes are rebuilt in stored order, matching
the legacy reader. Word counts use plain prose, excluding HTML duplicates,
image URLs and serialization syntax.

Unknown versions, malformed JSON and invalid page tags/fields fail explicitly.
They are never retried as legacy prose or silently replaced.

## Android: database 32 to 33

The migration adds `chapter_content.contentVersion INTEGER NOT NULL DEFAULT 1`.
It does not decode or rewrite old payloads, recalculate progress, or rebuild the
chapter table. Existing downloads retain their raw bytes and legacy semantics;
new writes store version 2. Reader, TTS, export and word-count backfill receive
both fields. Reads and exports use bounded blob chunks, so a multi-megabyte body
does not have to fit in one Android CursorWindow.

Legacy source strings were not escaped. If control characters already caused
ambiguous boundaries, their original meaning cannot be recovered from those
bytes alone. This migration preserves the old interpretation rather than guessing.
Saving a fresh copy from the source/import uses the safe new format.

## Desktop

Library metadata remains schema 1. Each downloaded chapter file is independently
versioned: old schema-1 files are interpreted as content version 1, and new
schema-2 files explicitly declare `contentVersion: 2`. Reads verify identity and
content digest before decoding. Reading an old chapter does not rewrite it;
replacing a download uses the existing atomic-file writer. Membership, source
identity, reading position and image indexes retain their existing behavior.

An older desktop build rejects a schema-2 chapter file. It does not silently read
the JSON payload as prose. Unsupported or inconsistent metadata is rejected and
the original file is preserved.

## Backup, export and rollback

In-app library backups remain version 2: they contain library/reading metadata,
not downloaded chapter bodies or image files. They are not full offline-data
rollback snapshots. No internal codec bytes or versions are added to that format.

EPUB export decodes the explicit stored version. XML 1.0 cannot represent the
reserved C0 controls, even as numeric references. Export rejects incompatible
text or metadata before opening the destination, rather than removing characters
or writing invalid XML. Valid Unicode and markup escaping remain supported.
This does not change EPUB navigation/chapter splitting (#411), or add rich-HTML
export beyond the existing exporter. See the [XML character rule](https://www.w3.org/TR/xml/#charsets).

**Downgrading Android:** the old app's Room database version is 32. It has no
33-to-32 migration and no destructive fallback, so it refuses the upgraded DB.
Do not clear app data to work around that guard. Reinstall the newer app to use
the intact DB, or restore a coherent full app-data copy made before the upgrade
when returning to the older app. That copy must include downloaded images and
the database with any WAL state; a library backup alone is insufficient.
[Room migration behavior](https://developer.android.com/training/data-storage/room/migrating-db-versions)
describes the default failure when a required migration is missing.

**Downgrading desktop:** retain a pre-update copy of the complete library
directory for the older build. Newly saved version-2 chapters have no automatic
legacy downgrade: that representation cannot express every source string safely.

## Verification

```sh
./gradlew :reading-core:test :desktop:test :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

`ChapterCodecMigrationTest` runs on native Android in the existing host workflow.
Its frozen v32 schema predates this change. It checks Room's full migration/schema
validation, byte-preserved legacy bodies over 2 MB, metadata/progress, chunked
exports, mixed version-2 writes, cancelled writes, unsupported-version rollback,
and reopen behavior. Native results are recorded separately from JVM tests.

After the merged commit produces a signed beta, dispatch
`chapter-codec-beta.yml` at that beta tag, with `tag` set to the same tag. This
CI-only harness uses a fresh API-35 emulator, verifies the frozen signed beta.342
baseline and target artifacts/signers, seeds a known v32 fixture, upgrades without
clearing data, verifies schema/content/reading metadata, restarts, checks actual
old-APK downgrade refusal, and recovers by reinstalling the new APK. It retains
the first result, logs and database copies. It does not claim a reader UI journey
or acceptance on the user's phone. Never run its DB-replacement setup on a personal
device. Stable release remains an explicit user decision after beta validation.
This signed check is pinned to the DB33 rollout and requires the beta.342 release
assets to remain available; retain its evidence before normal beta pruning.
