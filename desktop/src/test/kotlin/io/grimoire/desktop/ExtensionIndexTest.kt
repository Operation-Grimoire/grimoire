package io.grimoire.desktop

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExtensionIndexTest {
    @get:Rule val temporary = TemporaryFolder()
    private val repo = TestRepository()
    private val jar by lazy { fixtureJar(temporary.root.toPath()) }

    @Test fun validSignedIndexIsAccepted() {
        val bytes = repo.index(listOf(jar))
        val catalog = ExtensionIndex.verify(bytes, repo.sign(bytes), repo.trust, null)
        assertEquals(repo.repository, catalog.repository)
        assertEquals(listOf(jar.pkg), catalog.extensions.map { it.pkg })
        assertEquals(sha256(jar.bytes), catalog.extensions.single().artifact.sha256)
    }

    @Test fun modifiedBytesWrongKeyAndBrokenSignatureAreRejected() {
        val bytes = repo.index(listOf(jar))
        val signature = repo.sign(bytes)
        val modified = String(bytes).replace("\"en\"", "\"fr\"").toByteArray()
        rejects("signature does not match") { ExtensionIndex.verify(modified, signature, repo.trust, null) }
        val other = TestRepository(repository = repo.repository)
        rejects("signature does not match") { ExtensionIndex.verify(bytes, other.sign(bytes), repo.trust, null) }
        rejects("not valid base64") { ExtensionIndex.verify(bytes, "%%%".toByteArray(), repo.trust, null) }
        rejects("signature does not match") { ExtensionIndex.verify(bytes, "AAAA".toByteArray(), repo.trust, null) }
    }

    @Test fun indexForAnotherRepositoryIsRejectedEvenWhenSignedByTheEnrolledKey() {
        val bytes = repo.index(listOf(jar)).let { String(it).replace(repo.repository, "io.grimoire.other").toByteArray() }
        rejects("belongs to repository io.grimoire.other") { ExtensionIndex.verify(bytes, repo.sign(bytes), repo.trust, null) }
    }

    @Test fun replayedAndRewrittenIndexesAreRejected() {
        val current = repo.index(listOf(jar), sequence = 5)
        val accepted = AcceptedIndex(5, sha256(current))
        ExtensionIndex.verify(current, repo.sign(current), repo.trust, accepted)
        val older = repo.index(listOf(jar), sequence = 4)
        rejects("older than the one already accepted") { ExtensionIndex.verify(older, repo.sign(older), repo.trust, accepted) }
        val rewritten = repo.index(listOf(jar), sequence = 5) { entry, _ -> entry.put("name", "Renamed") }
        rejects("changed without a new sequence") { ExtensionIndex.verify(rewritten, repo.sign(rewritten), repo.trust, accepted) }
    }

    @Test fun malformedEntriesAreRejected() {
        val cases = mapOf<String, (JSONObject) -> Unit>(
            "Invalid artifact name" to { it.put("file", "../escape.jar") },
            "invalid sha256" to { it.put("sha256", "ABC") },
            "invalid size" to { it.put("size", ExtensionIndex.MAX_ARTIFACT_BYTES + 1) },
            "Invalid extension package" to { it.put("pkg", "com.example.evil") },
            "invalid source class" to { it.put("sourceClass", "io.grimoire.extension.other.Fixture") },
            "invalid API requirement" to { it.put("coreApi", "1.5.0") },
            "is not provided" to { it.put("hostLibraries", org.json.JSONArray(listOf("io.grimoire:extensions-lib:9.9.9"))) },
        )
        for ((message, edit) in cases) {
            val bytes = repo.index(listOf(jar)) { entry, _ -> edit(entry) }
            rejects(message) { ExtensionIndex.verify(bytes, repo.sign(bytes), repo.trust, null) }
        }
        val unknownFormat = String(repo.index(listOf(jar))).replace("\"format\":1", "\"format\":2").toByteArray()
        rejects("Unsupported extension index format") { ExtensionIndex.verify(unknownFormat, repo.sign(unknownFormat), repo.trust, null) }
    }

    @Test fun invalidPublicKeysAreRejected() {
        rejects("base64 Ed25519") { ExtensionIndex.publicKey("not a key") }
        rejects("base64 Ed25519") { ExtensionIndex.publicKey("AAAA") }
        assertEquals(39, ExtensionIndex.fingerprint(repo.publicKey).length)
    }

    private fun rejects(message: String, action: () -> Unit) {
        val failure = assertThrows(ExtensionIndexException::class.java) { action() }
        assertTrue("Expected '$message', got '${failure.message}'", failure.message!!.contains(message))
    }
}
