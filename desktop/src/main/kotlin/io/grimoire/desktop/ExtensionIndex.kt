package io.grimoire.desktop

import io.grimoire.reading.declaredExtensionApi
import io.grimoire.reading.supportsExtensionApi
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** An enrolled repository: the key is supplied out of band, never fetched from the repository. */
data class RepositoryTrust(val repository: String, val indexUrl: String, val publicKey: String)

data class IndexArtifact(val file: String, val sha256: String, val size: Long)

data class CatalogLibrary(val coordinate: String, val artifact: IndexArtifact)

data class CatalogExtension(
    val pkg: String,
    val name: String,
    val lang: String,
    val sourceClass: String,
    val versionCode: Int,
    val versionName: String,
    val sourceVersionCode: Int,
    val coreApi: String,
    val hostLibraries: List<String>,
    val artifact: IndexArtifact,
    val adultContent: String,
    val iconUrl: String?,
) {
    val metadata get() = ExtensionMetadata(pkg, sourceClass, versionCode, versionName, sourceVersionCode, coreApi, hostLibraries)
}

data class ExtensionCatalog(
    val repository: String,
    val sequence: Long,
    val libraries: List<CatalogLibrary>,
    val extensions: List<CatalogExtension>,
) {
    fun library(coordinate: String) = libraries.firstOrNull { it.coordinate == coordinate }

    /** Why this host cannot install [entry], or null. One unsupported entry never rejects the index. */
    fun compatibilityProblem(entry: CatalogExtension, hostApi: String): String? {
        val libraries = entry.hostLibraries.filter { it != HostRuntime.jsonCoordinate }
        return when {
            !supportsExtensionApi(entry.coreApi, hostApi) -> "Requires API ${entry.coreApi}"
            libraries.size > 1 -> "Requires more than one extensions library"
            libraries.any { library(it) == null } -> "Requires an unsupported host library"
            else -> null
        }
    }
}

/** Highest index accepted for a repository; replayed or rolled-back indexes are rejected against it. */
data class AcceptedIndex(val sequence: Long, val sha256: String)

class ExtensionIndexException(message: String) : IllegalArgumentException(message)

object ExtensionIndex {
    const val MAX_INDEX_BYTES = 2 * 1024 * 1024
    const val MAX_SIGNATURE_BYTES = 1024
    const val MAX_ARTIFACT_BYTES = 20L * 1024 * 1024

    private val pkgPattern = Regex("io\\.grimoire\\.extension(?:\\.[a-zA-Z_][a-zA-Z_0-9]*)+")
    private val filePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.jar")
    private val digestPattern = Regex("[0-9a-f]{64}")
    private val libraryPattern = Regex("io\\.grimoire:extensions-lib:[0-9][a-zA-Z0-9.\\-]*")

    /** Verifies the signature over the exact bytes before parsing anything else. */
    fun verify(bytes: ByteArray, signature: ByteArray, trust: RepositoryTrust, previous: AcceptedIndex?): ExtensionCatalog {
        if (bytes.size > MAX_INDEX_BYTES) fail("The extension index is too large")
        val decoded = try { Base64.getDecoder().decode(String(signature, Charsets.US_ASCII).trim()) }
        catch (_: IllegalArgumentException) { fail("The index signature is not valid base64") }
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey(trust.publicKey))
        verifier.update(bytes)
        val valid = try { decoded.size == 64 && verifier.verify(decoded) } catch (_: java.security.SignatureException) { false }
        if (!valid) fail("The index signature does not match the repository key")
        val catalog = parse(bytes)
        if (catalog.repository != trust.repository) fail("The index belongs to repository ${catalog.repository}, not ${trust.repository}")
        if (previous != null) {
            if (catalog.sequence < previous.sequence) fail("The index is older than the one already accepted")
            if (catalog.sequence == previous.sequence && sha256(bytes) != previous.sha256) fail("The index changed without a new sequence")
        }
        return catalog
    }

    fun parse(bytes: ByteArray): ExtensionCatalog = try {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.opt("format") != 1) fail("Unsupported extension index format")
        val repository = root.getString("repository").also { if (it.isBlank()) fail("Missing repository identifier") }
        val sequence = root.getLong("sequence").also { if (it <= 0) fail("Invalid index sequence") }
        val libraries = root.getJSONArray("libraries").objects().map { item ->
            val coordinate = item.getString("coordinate")
            if (!libraryPattern.matches(coordinate)) fail("Unsupported library $coordinate")
            CatalogLibrary(coordinate, artifact(item))
        }
        if (libraries.map { it.coordinate }.distinct().size != libraries.size) fail("Duplicate library in index")
        val extensions = root.getJSONArray("extensions").objects().map { item ->
            val pkg = item.getString("pkg")
            if (!pkgPattern.matches(pkg)) fail("Invalid extension package $pkg")
            val sourceClass = item.getString("sourceClass")
            if (!sourceClass.startsWith("$pkg.")) fail("$pkg: invalid source class")
            val coreApi = item.getString("coreApi")
            if (declaredExtensionApi(coreApi) == null) fail("$pkg: invalid API requirement")
            val hostLibraries = item.getJSONArray("hostLibraries").let { list -> (0 until list.length()).map(list::getString) }
            // Host-library support depends on this build, so it is judged per entry (see compatibilityProblem).
            CatalogExtension(
                pkg = pkg,
                name = item.getString("name").also { if (it.isBlank()) fail("$pkg: missing name") },
                lang = item.getString("lang"),
                sourceClass = sourceClass,
                versionCode = item.getInt("versionCode").also { if (it <= 0) fail("$pkg: invalid versionCode") },
                versionName = item.getString("versionName").also { if (it.isBlank()) fail("$pkg: missing versionName") },
                sourceVersionCode = item.getInt("sourceVersionCode").also { if (it <= 0) fail("$pkg: invalid sourceVersionCode") },
                coreApi = coreApi,
                hostLibraries = hostLibraries,
                artifact = artifact(item),
                adultContent = item.optString("adultContent", "NONE"),
                iconUrl = item.optString("iconUrl").takeIf { it.startsWith("https://") },
            )
        }
        if (extensions.map { it.pkg }.distinct().size != extensions.size) fail("Duplicate extension in index")
        ExtensionCatalog(repository, sequence, libraries, extensions.sortedBy { it.name.lowercase() })
    } catch (failure: JSONException) {
        fail("Invalid extension index: ${failure.message}")
    }

    private fun artifact(item: JSONObject): IndexArtifact {
        val file = item.getString("file")
        if (!filePattern.matches(file)) fail("Invalid artifact name $file")
        val digest = item.getString("sha256")
        if (!digestPattern.matches(digest)) fail("$file: invalid sha256")
        val size = item.getLong("size")
        if (size !in 1..MAX_ARTIFACT_BYTES) fail("$file: invalid size")
        return IndexArtifact(file, digest, size)
    }

    private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)

    private fun fail(message: String): Nothing = throw ExtensionIndexException(message)

    fun publicKey(base64: String): PublicKey {
        val raw = try { Base64.getDecoder().decode(base64.trim()) } catch (_: IllegalArgumentException) { null }
        if (raw == null || raw.size != 32) fail("The repository key must be a base64 Ed25519 public key")
        return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(ED25519_SPKI_PREFIX + raw))
    }

    /** Short form the user can compare with what the publisher shows. */
    fun fingerprint(base64: String): String {
        val raw = Base64.getDecoder().decode(base64.trim())
        return sha256(raw).take(32).chunked(4).joinToString(" ")
    }

    private val ED25519_SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
}

/** The official repository. Rotating or revoking its key ships as a Desktop update. */
val OFFICIAL_REPOSITORY = RepositoryTrust(
    repository = "io.grimoire.extensions",
    indexUrl = "https://github.com/Operation-Grimoire/grimoire-extensions/releases/download/latest/jar-index.json",
    publicKey = "PYUsj2AvddEkNAiO8Vc2tPskScoilyAdbMG8ZNN/IfM=",
)
