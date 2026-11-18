package io.grimoire.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

class ExtensionManagerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val host = "1.6.0"
    private val fetcher = FakeFetcher()
    private val repo = TestRepository()
    private val data: Path by lazy { temporary.newFolder("data").toPath() }
    private val jars: Path by lazy { temporary.newFolder("jars").toPath() }

    private fun <T> session(action: (ExtensionManager) -> T): T = LocalLibrary(data).use { library ->
        action(ExtensionManager(library, host, fetcher, listOf(repo.trust)))
    }

    private fun ExtensionManager.loadAll() = loadInstalled().also { (loaded, _) -> loaded.values.forEach { it.close() } }

    @Test fun installSurvivesRestartAndLoadsOfflineFromTheLocalStore() {
        val v1 = fixtureJar(jars)
        repo.publish(fetcher, listOf(v1))
        session { manager ->
            assertEquals(emptyList<String>(), manager.refresh())
            assertEquals(ExtensionStatus.AVAILABLE, manager.rows(emptyMap()).single().status)
            manager.install(repo.repository, v1.pkg).use { assertEquals("Fixture", it.source.name) }
        }
        fetcher.online = false
        fetcher.requests.clear()
        session { manager ->
            val (loaded, failures) = manager.loadInstalled()
            assertEquals(emptyMap<String, String>(), failures)
            loaded.getValue(v1.pkg).use { assertEquals(v1.versionName, it.versionName) }
            assertEquals(ExtensionStatus.INSTALLED, manager.rows(failures).single().status)
        }
        assertEquals(emptyList<String>(), fetcher.requests)
    }

    @Test fun removalForgetsTheExtensionAndCollectsItsArtifact() {
        val v1 = fixtureJar(jars)
        repo.publish(fetcher, listOf(v1))
        session { manager -> manager.refresh(); manager.install(repo.repository, v1.pkg).close(); manager.remove(v1.pkg) }
        session { manager ->
            assertTrue(manager.loadAll().first.isEmpty())
            assertEquals(ExtensionStatus.AVAILABLE, manager.rows(emptyMap()).single().status)
        }
        assertEquals(0L, Files.list(data.resolve("extensions/artifacts")).use { it.count() })
    }

    @Test fun tamperedDownloadIsRejectedBeforeAnyCodeRuns() {
        val v1 = fixtureJar(jars, pkg = "io.grimoire.extension.en.tampered")
        repo.publish(fetcher, listOf(v1))
        fetcher.files[repo.indexUrl.substringBeforeLast('/') + "/" + v1.file] = v1.bytes.copyOf().also { it[it.size - 1] = 1 }
        session { manager ->
            manager.refresh()
            assertThrowsMessage("digest does not match") { manager.install(repo.repository, v1.pkg) }
            assertTrue(manager.installed().isEmpty())
        }
        assertNull(System.getProperty(v1.initProperty))
    }

    @Test fun indexFieldThatDiffersFromTheJarManifestIsRejectedBeforeAnyCodeRuns() {
        val v1 = fixtureJar(jars, pkg = "io.grimoire.extension.en.mismatch")
        repo.publish(fetcher, listOf(v1), repo.index(listOf(v1)) { entry, _ -> entry.put("versionName", "9.9.9") })
        session { manager ->
            manager.refresh()
            assertThrowsMessage("JAR metadata mismatch: Grimoire-Version-Name") { manager.install(repo.repository, v1.pkg) }
            assertTrue(manager.installed().isEmpty())
        }
        assertNull(System.getProperty(v1.initProperty))
    }

    @Test fun updateThatFailsToLoadRollsBackToThePreviousVersion() {
        val pkg = "io.grimoire.extension.en.rollback"
        val v1 = fixtureJar(jars, pkg = pkg, versionCode = 1)
        val v2 = fixtureJar(jars, pkg = pkg, versionCode = 2, failOnLoad = true)
        repo.publish(fetcher, listOf(v1))
        session { manager -> manager.refresh(); manager.install(repo.repository, pkg).close() }
        repo.sequence = 2
        repo.publish(fetcher, listOf(v2))
        session { manager ->
            manager.loadAll()
            manager.refresh()
            assertEquals(ExtensionStatus.UPDATE, manager.rows(emptyMap()).single().status)
            assertThrows(Throwable::class.java) { manager.install(repo.repository, pkg) }
            assertEquals(1, manager.installed().single().current.metadata.versionCode)
        }
        session { manager -> manager.loadAll().first.let { assertEquals(setOf(pkg), it.keys) } }
    }

    @Test fun committedUpdateThatCannotLoadAtStartupFallsBackToThePreviousArtifact() {
        val pkg = "io.grimoire.extension.en.startup"
        val v1 = fixtureJar(jars, pkg = pkg, versionCode = 1)
        val v2 = fixtureJar(jars, pkg = pkg, versionCode = 2)
        repo.publish(fetcher, listOf(v1))
        session { manager -> manager.refresh(); manager.install(repo.repository, pkg).close() }
        session { manager -> manager.loadAll() }
        val firstMetadata = LocalLibrary(data).use { library ->
            library.extensionRead { db -> db.query("SELECT metadata FROM installed_extensions") { it.getString(1) }.single() }
        }
        repo.sequence = 2
        repo.publish(fetcher, listOf(v2))
        session { manager -> manager.refresh(); manager.install(repo.repository, pkg).close() }
        // Simulates a crash after the update committed but before it was activated, with its file lost.
        LocalLibrary(data).use { library ->
            library.extensionWrite { db -> db.execute("UPDATE installed_extensions SET previous=?", firstMetadata) }
        }
        Files.delete(data.resolve("extensions/artifacts/${sha256(v2.bytes)}.jar"))
        session { manager ->
            val (loaded, failures) = manager.loadInstalled()
            assertEquals(emptyMap<String, String>(), failures)
            loaded.getValue(pkg).use { assertEquals(v1.versionName, it.versionName) }
            assertEquals(1, manager.installed().single().current.metadata.versionCode)
            assertNull(manager.installed().single().previous)
        }
    }

    @Test fun downgradesAndEqualVersionsWithDifferentBytesAreNotOffered() {
        val pkg = "io.grimoire.extension.en.downgrade"
        val v2 = fixtureJar(jars, pkg = pkg, versionCode = 2)
        val v1 = fixtureJar(jars, pkg = pkg, versionCode = 1)
        val rebuilt = fixtureJar(jars, pkg = pkg, versionCode = 2, name = "Fixture")
        repo.publish(fetcher, listOf(v2))
        session { manager -> manager.refresh(); manager.install(repo.repository, pkg).close() }
        repo.sequence = 2
        repo.publish(fetcher, listOf(v1))
        session { manager ->
            manager.loadAll(); manager.refresh()
            assertEquals(ExtensionStatus.INSTALLED, manager.rows(emptyMap()).single().status)
            assertThrowsMessage("No newer version") { manager.install(repo.repository, pkg) }
        }
        repo.sequence = 3
        val conflicting = repo.index(listOf(rebuilt)) { entry, _ -> entry.put("sha256", "0".repeat(64)) }
        repo.publish(fetcher, listOf(rebuilt), conflicting)
        session { manager ->
            manager.loadAll(); manager.refresh()
            assertTrue(manager.rows(emptyMap()).any { it.status == ExtensionStatus.CONFLICT })
        }
    }

    @Test fun anotherRepositoryCannotReplaceAnInstalledPackage() {
        val v1 = fixtureJar(jars, pkg = "io.grimoire.extension.en.owned")
        val v2 = fixtureJar(jars, pkg = "io.grimoire.extension.en.owned", versionCode = 2)
        repo.publish(fetcher, listOf(v1))
        val other = TestRepository(repository = "io.grimoire.other")
        other.publish(fetcher, listOf(v2))
        LocalLibrary(data).use { library ->
            val manager = ExtensionManager(library, host, fetcher, listOf(repo.trust, other.trust))
            assertEquals(emptyList<String>(), manager.refresh())
            manager.install(repo.repository, v1.pkg).close()
            assertThrowsMessage("was installed from ${repo.repository}") { manager.install(other.repository, v1.pkg) }
        }
    }

    @Test fun replayedIndexIsRejectedAndTheAcceptedCatalogIsKept() {
        val v1 = fixtureJar(jars)
        repo.sequence = 5
        repo.publish(fetcher, listOf(v1))
        session { manager -> assertEquals(emptyList<String>(), manager.refresh()) }
        repo.sequence = 4
        repo.publish(fetcher, listOf())
        session { manager ->
            assertTrue(manager.refresh().single().contains("older than the one already accepted"))
            assertEquals(1, manager.repositoryViews().single().extensionCount)
        }
    }

    @Test fun changedArtifactRevokedTrustAndInterruptedDownloadsAreRecoveredAtStartup() {
        val v1 = fixtureJar(jars)
        val third = TestRepository(repository = "io.grimoire.third")
        val v3 = fixtureJar(jars, pkg = "io.grimoire.extension.en.third")
        repo.publish(fetcher, listOf(v1))
        third.publish(fetcher, listOf(v3))
        session { manager ->
            manager.refresh()
            manager.install(repo.repository, v1.pkg).close()
            val pending = manager.check(third.indexUrl, third.publicKey)
            assertEquals(third.repository, pending.trust.repository)
            manager.enroll(pending)
            manager.refresh()
            manager.install(third.repository, v3.pkg).close()
            manager.removeRepository(third.repository)
            assertThrowsMessage("can be disabled but not removed") { manager.removeRepository(repo.repository) }
        }
        Files.write(data.resolve("extensions/artifacts/${sha256(v1.bytes)}.jar"), byteArrayOf(1, 2, 3))
        val staged = Files.write(data.resolve("extensions/staging/download-1.jar"), byteArrayOf(1))
        session { manager ->
            assertFalse(Files.exists(staged))
            val (loaded, failures) = manager.loadInstalled()
            assertTrue(loaded.isEmpty())
            assertEquals("Extension file changed on disk", failures[v1.pkg])
            assertEquals("Repository ${third.repository} is no longer trusted", failures[v3.pkg])
        }
    }

    @Test fun addingARepositoryRequiresTheSignatureToMatchTheEnteredKey() {
        val other = TestRepository(repository = "io.grimoire.other")
        other.publish(fetcher, listOf(fixtureJar(jars)))
        session { manager ->
            val impostor = TestRepository(repository = "io.grimoire.other")
            assertThrows(ExtensionIndexException::class.java) { manager.check(other.indexUrl, impostor.publicKey) }
            assertThrowsMessage("must use HTTPS") { manager.check("http://repo.example/index.json", other.publicKey) }
            repo.publish(fetcher, listOf())
            assertThrowsMessage("already added") { manager.check(repo.indexUrl, repo.publicKey) }
        }
    }

    @Test fun browseListsInstalledSourcesAndDropsRemovedOnes() = runBlocking {
        val v1 = fixtureJar(jars, pkg = "io.grimoire.extension.en.browse", name = "Browse fixture")
        repo.publish(fetcher, listOf(v1))
        LocalLibrary(data).use { library ->
            val manager = ExtensionManager(library, host, fetcher, listOf(repo.trust))
            val model = desktopReaderModel(library, host, AtomicBoolean(true), manager)
            model.refreshRepositories(); model.settle()
            val row = model.state.value.extensionRows.single()
            model.installExtension(row); model.settle()
            assertEquals(null, model.state.value.error)
            assertEquals(listOf("Browse fixture"), model.state.value.sources.map { it.name })
            assertEquals("Browse fixture", model.state.value.sourceName)
            model.closeExtensionsOnly()
        }
        LocalLibrary(data).use { library ->
            val manager = ExtensionManager(library, host, fetcher, listOf(repo.trust))
            val model = desktopReaderModel(library, host, AtomicBoolean(true), manager)
            assertEquals(listOf("Browse fixture"), model.state.value.sources.map { it.name })
            model.removeExtension(model.state.value.extensionRows.single { it.status == ExtensionStatus.INSTALLED }); model.settle()
            assertTrue(model.state.value.sources.isEmpty())
            assertNull(model.state.value.sourceName)
            model.closeExtensionsOnly()
        }
    }

    @Test fun committedUpdateWhoseInitializerThrowsFallsBackAtStartupInsteadOfCrashing() = runBlocking {
        val pkg = "io.grimoire.extension.en.initerror"
        val v1 = fixtureJar(jars, pkg = pkg, versionCode = 1)
        val v2 = fixtureJar(jars, pkg = pkg, versionCode = 2, failOnLoad = true)
        repo.publish(fetcher, listOf(v1))
        session { manager -> manager.refresh(); manager.install(repo.repository, pkg).close() }
        // Crash after the update committed, before activation: v2 is current, v1 previous.
        Files.write(data.resolve("extensions/artifacts/${sha256(v2.bytes)}.jar"), v2.bytes)
        LocalLibrary(data).use { library ->
            library.extensionWrite { db ->
                val first = db.query("SELECT metadata FROM installed_extensions") { it.getString(1) }.single()
                val second = org.json.JSONObject(first).put("versionCode", 2).put("versionName", v2.versionName).put("sha256", sha256(v2.bytes))
                db.execute("UPDATE installed_extensions SET metadata=?, sha256=?, previous=?", second.toString(), sha256(v2.bytes), first)
            }
        }
        LocalLibrary(data).use { library ->
            val model = desktopReaderModel(library, host, AtomicBoolean(true), ExtensionManager(library, host, fetcher, listOf(repo.trust)))
            assertEquals(listOf("Fixture"), model.state.value.sources.map { it.name })
            model.close()
        }
        session { manager -> assertEquals(1, manager.installed().single().current.metadata.versionCode) }
    }

    @Test fun installedExtensionThatThrowsAnErrorIsReportedAndOthersStillLoad() = runBlocking {
        val good = fixtureJar(jars, pkg = "io.grimoire.extension.en.good", name = "Good")
        val bad = fixtureJar(jars, pkg = "io.grimoire.extension.en.bad", name = "Bad")
        repo.publish(fetcher, listOf(good, bad))
        session { manager ->
            manager.refresh()
            manager.install(repo.repository, good.pkg).close()
            manager.install(repo.repository, bad.pkg).close()
        }
        val broken = fixtureJar(jars, pkg = bad.pkg, name = "Bad", versionCode = bad.versionCode, failOnLoad = true)
        Files.write(data.resolve("extensions/artifacts/${sha256(broken.bytes)}.jar"), broken.bytes)
        LocalLibrary(data).use { library ->
            library.extensionWrite { db ->
                val metadata = db.query("SELECT metadata FROM installed_extensions WHERE pkg=?", bad.pkg) { it.getString(1) }.single()
                db.execute("UPDATE installed_extensions SET metadata=?, sha256=? WHERE pkg=?",
                    org.json.JSONObject(metadata).put("sha256", sha256(broken.bytes)).toString(), sha256(broken.bytes), bad.pkg)
            }
        }
        LocalLibrary(data).use { library ->
            val model = desktopReaderModel(library, host, AtomicBoolean(true), ExtensionManager(library, host, fetcher, listOf(repo.trust)))
            assertEquals(listOf("Good"), model.state.value.sources.map { it.name })
            // The changed record also differs from the catalog at the same version, so it shows a conflict too.
            assertEquals(setOf(ExtensionStatus.UNAVAILABLE, ExtensionStatus.CONFLICT),
                model.state.value.extensionRows.filter { it.pkg == bad.pkg }.map { it.status }.toSet())
            model.close()
        }
    }

    @Test fun unreadableStoredCatalogIsIsolatedToItsRepository() {
        val v1 = fixtureJar(jars)
        repo.publish(fetcher, listOf(v1))
        session { manager -> manager.refresh(); manager.install(repo.repository, v1.pkg).close() }
        LocalLibrary(data).use { library ->
            library.extensionWrite { db -> db.execute("UPDATE extension_repositories SET catalog=?", "{".toByteArray()) }
        }
        session { manager ->
            assertEquals(setOf(v1.pkg), manager.loadAll().first.keys)
            val view = manager.repositoryViews().single()
            assertFalse(view.refreshed)
            assertNotNull(view.problem)
            assertEquals(emptyList<String>(), manager.refresh())
            assertTrue(manager.repositoryViews().single().refreshed)
        }
    }

    @Test fun reenrollingARemovedRepositoryUnderAnotherKeyDoesNotTakeOverItsPackages() {
        val original = TestRepository(repository = "io.grimoire.shared")
        val v1 = fixtureJar(jars, pkg = "io.grimoire.extension.en.shared")
        val v2 = fixtureJar(jars, pkg = "io.grimoire.extension.en.shared", versionCode = 2)
        original.publish(fetcher, listOf(v1))
        session { manager ->
            manager.enroll(manager.check(original.indexUrl, original.publicKey))
            manager.install(original.repository, v1.pkg).close()
            manager.removeRepository(original.repository)
        }
        val impostor = TestRepository(repository = "io.grimoire.shared", indexUrl = "https://evil.example/jar-index.json")
        impostor.sequence = 9
        impostor.publish(fetcher, listOf(v2))
        session { manager ->
            val pending = manager.check(impostor.indexUrl, impostor.publicKey)
            assertNotNull(pending.warning)
            manager.enroll(pending)
            val (loaded, failures) = manager.loadAll()
            assertTrue(loaded.isEmpty())
            assertEquals("Repository io.grimoire.shared is no longer trusted", failures[v1.pkg])
            assertThrowsMessage("under another key") { manager.install(impostor.repository, v1.pkg) }
        }
    }

    @Test fun reenrollingTheSameKeyRestoresItsPackages() {
        val other = TestRepository(repository = "io.grimoire.same")
        val v1 = fixtureJar(jars, pkg = "io.grimoire.extension.en.same")
        other.publish(fetcher, listOf(v1))
        session { manager ->
            manager.enroll(manager.check(other.indexUrl, other.publicKey))
            manager.install(other.repository, v1.pkg).close()
            manager.removeRepository(other.repository)
            assertNull(manager.check(other.indexUrl, other.publicKey).warning)
            manager.enroll(manager.check(other.indexUrl, other.publicKey))
            val (loaded, failures) = manager.loadInstalled { it.repository == other.repository }
            loaded.values.forEach { it.close() }
            assertEquals(setOf(v1.pkg), loaded.keys)
            assertTrue(failures.isEmpty())
        }
    }

    @Test fun builtinDroppedByADesktopUpdateIsRevoked() {
        val v1 = fixtureJar(jars, pkg = "io.grimoire.extension.en.dropped")
        repo.publish(fetcher, listOf(v1))
        session { manager -> manager.refresh(); manager.install(repo.repository, v1.pkg).close() }
        LocalLibrary(data).use { library ->
            val manager = ExtensionManager(library, host, fetcher, emptyList())
            assertTrue(manager.repositoryViews().isEmpty())
            assertEquals("Repository ${repo.repository} is no longer trusted", manager.loadAll().second[v1.pkg])
        }
    }

    @Test fun listingNameMustMatchTheSourceAndQueryUrlsAreRefused() {
        val v1 = fixtureJar(jars, pkg = "io.grimoire.extension.en.renamed", name = "Real name")
        repo.publish(fetcher, listOf(v1), repo.index(listOf(v1)) { entry, _ -> entry.put("name", "Listed name") })
        session { manager ->
            manager.refresh()
            assertThrowsMessage("does not match the repository listing") { manager.install(repo.repository, v1.pkg) }
            assertTrue(manager.installed().isEmpty())
            assertThrowsMessage("query or fragment") { manager.check("https://repo.example/jar-index.json?token=1", repo.publicKey) }
        }
    }

    @Test fun sql4StoreUpgradesAtomicallyAndKeepsItsLibrary() {
        LocalLibrary(data).use { it.seed() }
        mutateDatabase(data, "DROP TABLE extension_repositories")
        mutateDatabase(data, "DROP TABLE installed_extensions")
        mutateDatabase(data, "PRAGMA user_version=4")
        try { LocalLibrary(data) { if (it == StorageCheckpoint.BEFORE_COMMIT) throw java.io.IOException("Migration fault") }; fail("upgrade must roll back") }
        catch (e: java.io.IOException) { assertEquals("Migration fault", e.message) }
        java.sql.DriverManager.getConnection("jdbc:sqlite:${data.resolve("library.sqlite")}").use { db ->
            assertEquals(listOf(4L), db.query("PRAGMA user_version") { it.getLong(1) })
            assertEquals(listOf(0L), db.query("SELECT count(*) FROM sqlite_master WHERE name LIKE '%extension%'") { it.getLong(1) })
        }
        session { manager ->
            assertEquals(listOf(repo.repository), manager.repositoryViews().map { it.id })
            assertTrue(manager.installed().isEmpty())
        }
        LocalLibrary(data).use { assertNotNull(it.book(fixtureKey.novel)) }
    }

    private suspend fun ReaderModel.settle() = withTimeout(10_000) { state.first { !it.busy } }

    private suspend fun ReaderModel.closeExtensionsOnly() { settle(); close() }

    private fun assertThrowsMessage(message: String, action: () -> Unit) {
        val failure = assertThrows(Exception::class.java) { action() }
        assertTrue("Expected '$message', got '${failure.message}'", failure.message!!.contains(message))
    }
}
