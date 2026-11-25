package io.grimoire.desktop

import io.grimoire.api.model.novel.*
import io.grimoire.reading.*
import io.grimoire.reading.storage.*
import io.grimoire.ui.theme.ColorTheme
import io.grimoire.ui.theme.ThemeMode
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files

class LocalLibraryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun legacyChapterReadsWithoutRewritingBytesAndRetainsPosition() {
        val directory = temporary.newFolder().toPath()
        val file = legacyFixture(directory)
        val manifest = Files.readAllBytes(directory.resolve("library.json"))
        val body = Files.readAllBytes(file)
        LocalLibrary(directory).use { store ->
            assertEquals(PageContent.Text("Legacy prose", "<i>Legacy prose</i>"), store.downloaded(fixtureKey)!!.single().content)
            assertEquals(ReadPosition("/chapter", 0, 17), store.saved().single().position)
        }
        assertArrayEquals(manifest, Files.readAllBytes(directory.resolve("library.json.before-sqlite")))
        assertArrayEquals(body, Files.readAllBytes(file))
        // Same predicate used by the old writer: it refuses schema2 before touching any book.
        assertNotEquals(1, JSONObject(Files.readString(directory.resolve("library.json"))).getInt("schema"))
        LocalLibrary(directory).use { assertEquals(ReadPosition("/chapter", 0, 17), it.saved().single().position) }
    }

    @Test fun appearanceDefaultsPersistsAcrossReopenAndIgnoresUnknownValues() {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { store ->
            assertEquals(DesktopAppearance(ThemeMode.SYSTEM, ColorTheme.DEFAULT), store.appearance())
            store.setAppearance(DesktopAppearance(ThemeMode.DARK, ColorTheme.OCEAN))
        }
        LocalLibrary(directory).use { store ->
            assertEquals(DesktopAppearance(ThemeMode.DARK, ColorTheme.OCEAN), store.appearance())
            // A value written by a newer build falls back instead of failing startup, and changing the other field preserves it.
            store.extensionWrite { it.execute("UPDATE settings SET value='SEPIA' WHERE name='color_theme'") }
            assertEquals(DesktopAppearance(ThemeMode.DARK, ColorTheme.DEFAULT), store.appearance())
            store.setThemeMode(ThemeMode.LIGHT)
            assertEquals(DesktopAppearance(ThemeMode.LIGHT, ColorTheme.DEFAULT), store.appearance())
            val rawPalette = store.extensionRead { db ->
                db.query("SELECT value FROM settings WHERE name='color_theme'") { it.getString(1) }.singleOrNull()
            }
            assertEquals("SEPIA", rawPalette)
            store.extensionWrite { it.execute("UPDATE settings SET value='EINK' WHERE name='theme_mode'") }
            store.setColorTheme(ColorTheme.FOREST)
            assertEquals(DesktopAppearance(ThemeMode.SYSTEM, ColorTheme.FOREST), store.appearance())
            val rawMode = store.extensionRead { db ->
                db.query("SELECT value FROM settings WHERE name='theme_mode'") { it.getString(1) }.singleOrNull()
            }
            assertEquals("EINK", rawMode)
        }
    }

    @Test fun versionTwoImportPreservesControlCharactersAndExactLegacyBytes() {
        val directory = temporary.newFolder().toPath()
        val text = "Prose\u0000\u001c\u001d\u001e\u001f\u4e2d\u6587\uD83D\uDE00"
        val pages = prose(text)
        val file = legacyFixture(directory, 2, encodeChapterContent(pages).content)
        val before = Files.readAllBytes(file)
        LocalLibrary(directory).use { assertEquals(pages, it.downloaded(fixtureKey)) }
        assertArrayEquals(before, Files.readAllBytes(file))
    }

    @Test fun invalidLegacyMetadataOrMissingBodyNeverCreatesAnEmptyStore() {
        for (invalid in listOf(1, 99, "2", 2.5, 4_294_967_298L, null)) {
            val directory = temporary.newFolder().toPath()
            val file = legacyFixture(directory, 2, encodeChapterContent(prose()).content)
            if (invalid == null) Files.delete(file)
            else Files.writeString(file, JSONObject(Files.readString(file)).put("contentVersion", invalid).toString())
            val manifest = Files.readAllBytes(directory.resolve("library.json"))
            try { LocalLibrary(directory).close(); fail("Accepted invalid legacy body: $invalid") }
            catch (_: Exception) { }
            assertFalse(Files.exists(directory.resolve("library.sqlite")))
            assertArrayEquals(manifest, Files.readAllBytes(directory.resolve("library.json")))
        }
    }

    @Test fun corruptLegacyIdentityDigestAndCodecFailWithoutRewritingEvidence() {
        for (field in listOf("sourceId", "sha256", "schema")) {
            val directory = temporary.newFolder().toPath()
            val file = legacyFixture(directory)
            val value: Any = if (field == "sha256") "wrong" else 99
            val damaged = JSONObject(Files.readString(file)).put(field, value).toString()
            Files.writeString(file, damaged)
            try { LocalLibrary(directory).close(); fail("Accepted corrupt $field") } catch (_: IllegalArgumentException) { }
            assertEquals(damaged, Files.readString(file))
            assertFalse(Files.exists(directory.resolve("library.sqlite")))
        }
    }

    @Test fun removingMembershipPreservesContentProgressAndSourceIsolation() {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { store ->
            for (source in listOf(17L, 18L)) {
                val key = fixtureKey.copy(novel = NovelKey(source, fixtureKey.novel.url))
                store.remember(key.novel, fixtureNovel, fixtureChapters)
                store.download(key, prose("Source $source"))
            }
            store.position(fixtureKey, 0, 37)
            store.favorite(fixtureKey.novel, true)
            store.favorite(fixtureKey.novel, false)
        }
        LocalLibrary(directory).use { store ->
            assertTrue(store.saved().isEmpty())
            assertEquals(ReadPosition("/chapter", 0, 37), store.book(fixtureKey.novel)!!.position)
            for (source in listOf(17L, 18L)) assertEquals(prose("Source $source"), store.downloaded(fixtureKey.copy(novel = NovelKey(source, "/novel"))))
            assertNull(store.downloaded(fixtureKey.copy(novel = NovelKey(19, "/novel"))))
            assertNull(store.downloaded(fixtureKey.copy(url = "/missing")))
        }
    }

    @Test fun failedMembershipTransactionDoesNotPublishAndPendingPositionCanRetry() {
        val directory = temporary.newFolder().toPath()
        var inject = false
        LocalLibrary(directory) { if (inject && it == StorageCheckpoint.BEFORE_COMMIT) throw IOException("disk failure") }.use { store ->
            store.seed()
            store.position(fixtureKey, 0, 21)
            inject = true
            try { store.favorite(fixtureKey.novel, true); fail("Write succeeded") } catch (_: IOException) { }
            assertFalse(store.book(fixtureKey.novel)!!.favorite)
            try { store.flush(); fail("Flush succeeded") } catch (_: IOException) { }
            assertEquals(ReadPosition("/chapter", 0, 21), store.book(fixtureKey.novel)!!.position)
            inject = false
            store.flush()
            store.favorite(fixtureKey.novel, true)
        }
        LocalLibrary(directory).use {
            assertTrue(it.saved().single().favorite)
            assertEquals(ReadPosition("/chapter", 0, 21), it.saved().single().position)
        }
    }

    @Test fun scrollAfterFailedAutosavePersistsLatestPositionWithoutAnInterveningRead() {
        for (point in listOf(StorageCheckpoint.BEFORE_COMMIT, StorageCheckpoint.AFTER_COMMIT)) {
            val directory = temporary.newFolder().toPath()
            var inject = false
            LocalLibrary(directory) { if (inject && it == point) throw IOException("autosave failed") }.use { store ->
                store.seed()
                store.position(fixtureKey, 0, 10)
                inject = true
                try { store.flush(); fail("Flush reply delivered") } catch (_: IOException) { }
                inject = false
                // Matches the reader scroll flow: next position arrives before any lookup or retry.
                store.position(fixtureKey, 0, 42)
                store.flush()
            }
            LocalLibrary(directory).use { assertEquals(ReadPosition("/chapter", 0, 42), it.book(fixtureKey.novel)!!.position) }
        }
    }

    @Test fun postCommitFailureDoesNotPublishAnOldMemorySnapshotOverTheCommittedState() {
        val directory = temporary.newFolder().toPath()
        var inject = false
        LocalLibrary(directory) { if (inject && it == StorageCheckpoint.AFTER_COMMIT) throw IOException("reply lost") }.use { store ->
            store.seed()
            inject = true
            try { store.favorite(fixtureKey.novel, true); fail("Reply delivered") } catch (_: IOException) { }
            inject = false
            assertTrue(store.book(fixtureKey.novel)!!.favorite)
            store.position(fixtureKey, 0, 22)
            store.flush()
        }
        LocalLibrary(directory).use { assertTrue(it.saved().single().favorite) }
    }

    @Test fun lockedOrUnreadableDownloadCannotCommit() {
        LocalLibrary(temporary.newFolder().toPath()).use { store ->
            store.seed()
            try { store.download(fixtureKey.copy(url = "/locked"), prose()); fail("Locked save") } catch (_: IllegalArgumentException) { }
            try { store.download(fixtureKey, emptyList()); fail("Empty save") } catch (_: IllegalArgumentException) { }
            assertTrue(store.book(fixtureKey.novel)!!.downloaded.isEmpty())
        }
    }

    @Test fun corruptSqlBodiesRemainIndexedAndNeverFallBackToLegacyFiles() {
        for (sql in listOf("content='damaged'", "content=NULL", "content_version=99", "content_version='wrong'", "content_version=2.5", "content_version=4294967298", "digest='wrong'")) {
            val directory = temporary.newFolder().toPath()
            val legacy = legacyFixture(directory)
            val before = Files.readAllBytes(legacy)
            LocalLibrary(directory).close()
            mutateDatabase(directory, "UPDATE contents SET $sql")
            LocalLibrary(directory).use { store ->
                assertTrue("/chapter" in store.book(fixtureKey.novel)!!.downloaded)
                try { store.downloaded(fixtureKey); fail("Accepted $sql") } catch (_: IllegalArgumentException) { }
            }
            assertArrayEquals(before, Files.readAllBytes(legacy))
        }
    }

    @Test fun changedLegacyManifestAfterHandoffFailsClosedWithoutOverwritingIt() {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { store ->
            store.seed()
            val marker = Files.readAllBytes(directory.resolve("library.json"))
            val original = "{\"schema\":1,\"books\":[]}"
            Files.writeString(directory.resolve("library.json"), original)
            try { store.favorite(fixtureKey.novel, true); fail("Old writer was ignored") } catch (_: IOException) { }
            assertEquals(original, Files.readString(directory.resolve("library.json")))
            Files.write(directory.resolve("library.json"), marker)
            assertFalse(store.book(fixtureKey.novel)!!.favorite)
        }
    }

    @Test fun unsupportedDatabaseAndUnreadableManifestAreNeverReset() {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { it.seed() }
        mutateDatabase(directory, "PRAGMA user_version=99")
        try { LocalLibrary(directory).close(); fail("Unknown database version reset") } catch (_: IllegalArgumentException) { }
        val corrupt = temporary.newFolder().toPath()
        Files.writeString(corrupt.resolve("library.json"), "{broken")
        try { LocalLibrary(corrupt).close(); fail("Corrupt JSON reset") } catch (_: org.json.JSONException) { }
        assertEquals("{broken", Files.readString(corrupt.resolve("library.json")))
    }

    @Test fun secondOwnerIsRejectedUntilExplicitClose() {
        val directory = temporary.newFolder().toPath()
        LocalLibrary(directory).use { first ->
            first.seed()
            try { LocalLibrary(directory).close(); fail("Second owner opened") } catch (_: IOException) { }
        }
        LocalLibrary(directory).use { assertNotNull(it.book(fixtureKey.novel)) }
    }

    @Test fun incompleteHandoffCanResumeButChangedLegacySourceCannot() {
        for (checkpoint in listOf(StorageCheckpoint.BEFORE_HANDOFF, StorageCheckpoint.AFTER_HANDOFF)) {
            val directory = temporary.newFolder().toPath()
            val legacy = legacyFixture(directory)
            val before = Files.readAllBytes(legacy)
            try { LocalLibrary(directory) { if (it == checkpoint) throw IOException("interrupted") }; fail("Did not interrupt") } catch (_: IOException) { }
            LocalLibrary(directory).use { assertEquals(ReadPosition("/chapter", 0, 17), it.saved().single().position) }
            assertArrayEquals(before, Files.readAllBytes(legacy))
        }
        val changed = temporary.newFolder().toPath()
        val legacy = legacyFixture(changed)
        try { LocalLibrary(changed) { if (it == StorageCheckpoint.BEFORE_HANDOFF) throw IOException("interrupted") } } catch (_: IOException) { }
        Files.writeString(legacy, "changed")
        try { LocalLibrary(changed).close(); fail("Changed source imported") } catch (_: IllegalArgumentException) { }
        assertEquals("changed", Files.readString(legacy))
        assertEquals(1, JSONObject(Files.readString(changed.resolve("library.json"))).getInt("schema"))
    }
}
