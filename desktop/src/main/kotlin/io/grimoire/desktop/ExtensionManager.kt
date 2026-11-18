package io.grimoire.desktop

import io.grimoire.reading.supportsExtensionApi
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.sql.Connection
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Downloads at most [limit] bytes; implementations must refuse anything but HTTPS. */
fun interface ArtifactFetcher {
    fun fetch(url: String, limit: Long): ByteArray
}

class HttpArtifactFetcher(private val networkEnabled: AtomicBoolean) : ArtifactFetcher {
    private val client = OkHttpClient.Builder()
        .followSslRedirects(false)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    override fun fetch(url: String, limit: Long): ByteArray {
        if (!networkEnabled.get()) throw IOException("Offline mode is enabled")
        require(url.startsWith("https://")) { "Extension downloads require HTTPS: $url" }
        val request = Request.Builder().url(url).header("User-Agent", "Grimoire-Desktop/0.1").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            require(response.request.url.isHttps) { "Extension download left HTTPS: $url" }
            val bytes = checkNotNull(response.body).byteStream().use { it.readNBytes((limit + 1).toInt()) }
            if (bytes.size > limit) throw IOException("Download exceeds $limit bytes: $url")
            return bytes
        }
    }
}

/** One installed artifact set. The digest is checked again before every load. */
internal data class InstalledArtifact(
    val metadata: ExtensionMetadata,
    val name: String,
    val lang: String,
    val sha256: String,
    val libraryCoordinate: String?,
    val librarySha256: String?,
)

internal data class InstalledRecord(val repository: String, val current: InstalledArtifact, val previous: InstalledArtifact?) {
    val pkg get() = current.metadata.pkg
}

internal data class RepositoryRecord(
    val trust: RepositoryTrust,
    val accepted: AcceptedIndex?,
    val catalog: ExtensionCatalog?,
    val enabled: Boolean,
    val builtin: Boolean,
)

/** A verified index whose key the user has not confirmed yet. */
data class PendingRepository(val trust: RepositoryTrust, val fingerprint: String, val catalog: ExtensionCatalog, internal val bytes: ByteArray)

data class RepositoryView(
    val id: String,
    val indexUrl: String,
    val fingerprint: String,
    val builtin: Boolean,
    val enabled: Boolean,
    val refreshed: Boolean,
    val extensionCount: Int,
)

enum class ExtensionStatus { INSTALLED, UPDATE, AVAILABLE, INCOMPATIBLE, CONFLICT, UNAVAILABLE }

data class ExtensionRow(
    val pkg: String,
    val name: String,
    val lang: String,
    val repository: String,
    val installedVersion: String?,
    val availableVersion: String?,
    val status: ExtensionStatus,
    val reason: String? = null,
)

/**
 * Repository trust, catalogs and installed artifacts (docs/desktop-extensions.md).
 * Callers serialize operations; the database transactions are the commit points.
 */
class ExtensionManager(
    private val library: LocalLibrary,
    private val hostApi: String,
    private val fetcher: ArtifactFetcher,
    builtins: List<RepositoryTrust>,
) {
    private val staging = library.extensionDirectory.resolve("staging")
    private val artifacts = library.extensionDirectory.resolve("artifacts")

    init {
        Files.createDirectories(staging)
        Files.createDirectories(artifacts)
        // Interrupted downloads never resume.
        Files.list(staging).use { files -> files.forEach(Files::deleteIfExists) }
        library.extensionWrite { db -> builtins.forEach { trust ->
            db.execute("""INSERT INTO extension_repositories(repository,index_url,public_key,builtin) VALUES(?,?,?,1)
                ON CONFLICT(repository) DO UPDATE SET index_url=excluded.index_url, public_key=excluded.public_key, builtin=1""",
                trust.repository, trust.indexUrl, trust.publicKey)
        } }
    }

    internal fun repositories(): List<RepositoryRecord> = library.extensionRead(::readRepositories)

    internal fun installed(): List<InstalledRecord> = library.extensionRead(::readInstalled)

    fun repositoryViews(): List<RepositoryView> = repositories().map { record ->
        RepositoryView(record.trust.repository, record.trust.indexUrl, ExtensionIndex.fingerprint(record.trust.publicKey),
            record.builtin, record.enabled, record.catalog != null, record.catalog?.extensions?.size ?: 0)
    }

    /** Refreshes every enabled repository; returns one message per repository that failed. */
    fun refresh(): List<String> = repositories().filter { it.enabled }.mapNotNull { record ->
        try {
            val (bytes, catalog) = fetchIndex(record.trust, record.accepted)
            val accepted = AcceptedIndex(catalog.sequence, sha256(bytes))
            if (accepted != record.accepted) library.extensionWrite { db ->
                val stored = db.query("SELECT sequence FROM extension_repositories WHERE repository=?", record.trust.repository) { it.getLong(1) }.single()
                check(stored <= catalog.sequence) { "A newer index was accepted concurrently" }
                db.execute("UPDATE extension_repositories SET sequence=?, index_sha256=?, catalog=? WHERE repository=?",
                    accepted.sequence, accepted.sha256, bytes, record.trust.repository)
            }
            null
        } catch (failure: Exception) {
            "${record.trust.repository}: ${failure.message ?: failure.javaClass.simpleName}"
        }
    }

    /** Downloads and verifies a repository the user wants to add. Nothing is stored yet. */
    fun check(indexUrl: String, publicKey: String): PendingRepository {
        val url = indexUrl.trim()
        require(url.startsWith("https://")) { "Repository URL must use HTTPS" }
        val key = publicKey.trim()
        ExtensionIndex.publicKey(key)
        val bytes = fetcher.fetch(url, ExtensionIndex.MAX_INDEX_BYTES.toLong())
        val signature = fetcher.fetch("$url.sig", ExtensionIndex.MAX_SIGNATURE_BYTES.toLong())
        // The identifier is read from the index, but only trusted once the signature verifies.
        val claimed = ExtensionIndex.parse(bytes).repository
        val trust = RepositoryTrust(claimed, url, key)
        val catalog = ExtensionIndex.verify(bytes, signature, trust, null)
        require(repositories().none { it.trust.repository == claimed }) { "Repository $claimed is already added" }
        return PendingRepository(trust, ExtensionIndex.fingerprint(key), catalog, bytes)
    }

    fun enroll(pending: PendingRepository) = library.extensionWrite { db ->
        val existing = db.query("SELECT 1 FROM extension_repositories WHERE repository=?", pending.trust.repository) { 1 }
        require(existing.isEmpty()) { "Repository ${pending.trust.repository} is already added" }
        db.execute("""INSERT INTO extension_repositories(repository,index_url,public_key,sequence,index_sha256,catalog,enabled,builtin)
            VALUES(?,?,?,?,?,?,1,0)""", pending.trust.repository, pending.trust.indexUrl, pending.trust.publicKey,
            pending.catalog.sequence, sha256(pending.bytes), pending.bytes)
    }

    fun setEnabled(repository: String, enabled: Boolean) = library.extensionWrite { db ->
        db.execute("UPDATE extension_repositories SET enabled=? WHERE repository=?", if (enabled) 1 else 0, repository)
    }

    /** Revokes trust. Installed extensions from it stop loading; library data is kept. */
    fun removeRepository(repository: String) = library.extensionWrite { db ->
        val builtin = db.query("SELECT builtin FROM extension_repositories WHERE repository=?", repository) { it.getInt(1) }.singleOrNull()
        require(builtin != 1) { "The official repository can be disabled but not removed" }
        db.execute("DELETE FROM extension_repositories WHERE repository=?", repository)
    }

    /** Installs or updates [pkg]; the returned extension is active and the record committed. */
    fun install(repository: String, pkg: String): LocalExtension {
        val record = repositories().firstOrNull { it.trust.repository == repository } ?: error("Repository $repository is not trusted")
        check(record.enabled) { "Repository $repository is disabled" }
        val catalog = record.catalog ?: error("Refresh $repository before installing")
        val entry = catalog.extensions.firstOrNull { it.pkg == pkg } ?: error("$pkg is not offered by $repository")
        val existing = installed().firstOrNull { it.pkg == pkg }
        if (existing != null) {
            require(existing.repository == repository) { "$pkg was installed from ${existing.repository}. Remove it first." }
            require(entry.versionCode > existing.current.metadata.versionCode) { "No newer version of ${entry.name} is available" }
        }
        require(supportsExtensionApi(entry.coreApi, hostApi)) { "${entry.name} requires API ${entry.coreApi}; Desktop provides $hostApi" }
        val libraryEntry = entry.hostLibraries.filter { it != HostRuntime.jsonCoordinate }
            .also { require(it.size <= 1) { "${entry.name} requires more than one extensions library" } }
            .firstOrNull()?.let { catalog.library(it) ?: error("Missing library $it") }

        val libraryBytes = libraryEntry?.let { download(record.trust, it.artifact) }
        val sourceBytes = download(record.trust, entry.artifact)
        val libraryArtifact = libraryEntry?.let { LibraryArtifact(it.coordinate, libraryBytes!!) }
        LocalExtension.verify(sourceBytes, entry.metadata, libraryArtifact, hostApi)
        libraryEntry?.let { publish(it.artifact.sha256, libraryBytes!!) }
        publish(entry.artifact.sha256, sourceBytes)

        val artifact = InstalledArtifact(entry.metadata, entry.name, entry.lang, entry.artifact.sha256,
            libraryEntry?.coordinate, libraryEntry?.artifact?.sha256)
        commit(InstalledRecord(repository, artifact, existing?.current))
        val loaded = try {
            LocalExtension.load(sourceBytes, entry.metadata, libraryArtifact, hostApi)
        } catch (failure: Throwable) {
            try { if (existing != null) commit(existing.copy(previous = null)) else delete(pkg) }
            catch (rollback: Throwable) { failure.addSuppressed(rollback) }
            throw failure
        }
        commit(InstalledRecord(repository, artifact, null))
        return loaded
    }

    fun remove(pkg: String) = delete(pkg)

    /**
     * Loads every installed extension from the local store. An update that was committed
     * but never activated falls back to its previous artifact when the new one fails.
     */
    fun loadInstalled(): Pair<Map<String, LocalExtension>, Map<String, String>> {
        val trusted = repositories().map { it.trust.repository }.toSet()
        val records = installed()
        collectUnreferenced(records)
        val loaded = linkedMapOf<String, LocalExtension>()
        val failures = linkedMapOf<String, String>()
        for (record in records) {
            if (record.repository !in trusted) { failures[record.pkg] = "Repository ${record.repository} is no longer trusted"; continue }
            try {
                loaded[record.pkg] = load(record.current)
                if (record.previous != null) commit(record.copy(previous = null))
            } catch (failure: Exception) {
                val previous = record.previous
                val restored = previous?.let { runCatching { load(it) }.getOrNull() }
                if (restored != null) {
                    loaded[record.pkg] = restored
                    runCatching { commit(InstalledRecord(record.repository, previous, null)) }
                } else {
                    failures[record.pkg] = failure.message ?: failure.javaClass.simpleName
                }
            }
        }
        return loaded to failures
    }

    fun rows(failures: Map<String, String>): List<ExtensionRow> {
        val installed = installed().associateBy { it.pkg }
        val repositories = repositories()
        val rows = mutableListOf<ExtensionRow>()
        for (record in installed.values) {
            val offered = repositories.firstOrNull { it.trust.repository == record.repository }
                ?.catalog?.extensions?.firstOrNull { it.pkg == record.pkg }
            val current = record.current
            val status = when {
                record.pkg in failures -> ExtensionStatus.UNAVAILABLE
                offered != null && offered.versionCode > current.metadata.versionCode &&
                    supportsExtensionApi(offered.coreApi, hostApi) -> ExtensionStatus.UPDATE
                else -> ExtensionStatus.INSTALLED
            }
            rows += ExtensionRow(record.pkg, current.name, current.lang, record.repository, current.metadata.versionName,
                offered?.versionName, status, failures[record.pkg])
        }
        for (repository in repositories.filter { it.enabled }) {
            for (entry in repository.catalog?.extensions.orEmpty()) {
                val owner = installed[entry.pkg]
                if (owner != null) {
                    val sameVersion = owner.current.metadata.versionCode == entry.versionCode
                    if (owner.repository == repository.trust.repository && sameVersion && owner.current.sha256 != entry.artifact.sha256) {
                        rows += ExtensionRow(entry.pkg, entry.name, entry.lang, repository.trust.repository, owner.current.metadata.versionName,
                            entry.versionName, ExtensionStatus.CONFLICT, "Same version with different contents")
                    }
                    continue
                }
                val compatible = supportsExtensionApi(entry.coreApi, hostApi)
                rows += ExtensionRow(entry.pkg, entry.name, entry.lang, repository.trust.repository, null, entry.versionName,
                    if (compatible) ExtensionStatus.AVAILABLE else ExtensionStatus.INCOMPATIBLE,
                    if (compatible) null else "Requires API ${entry.coreApi}")
            }
        }
        return rows
    }

    private fun load(artifact: InstalledArtifact): LocalExtension {
        val source = readArtifact(artifact.sha256)
        val library = artifact.libraryCoordinate?.let { LibraryArtifact(it, readArtifact(checkNotNull(artifact.librarySha256))) }
        return LocalExtension.load(source, artifact.metadata, library, hostApi)
    }

    private fun readArtifact(digest: String): ByteArray {
        val path = artifacts.resolve("$digest.jar")
        if (!Files.isRegularFile(path)) throw IOException("Extension file is missing")
        val bytes = Files.newInputStream(path).use { it.readNBytes(ExtensionIndex.MAX_ARTIFACT_BYTES.toInt() + 1) }
        if (bytes.size > ExtensionIndex.MAX_ARTIFACT_BYTES || sha256(bytes) != digest) throw IOException("Extension file changed on disk")
        return bytes
    }

    private fun fetchIndex(trust: RepositoryTrust, previous: AcceptedIndex?): Pair<ByteArray, ExtensionCatalog> {
        val bytes = fetcher.fetch(trust.indexUrl, ExtensionIndex.MAX_INDEX_BYTES.toLong())
        val signature = fetcher.fetch("${trust.indexUrl}.sig", ExtensionIndex.MAX_SIGNATURE_BYTES.toLong())
        return bytes to ExtensionIndex.verify(bytes, signature, trust, previous)
    }

    private fun download(trust: RepositoryTrust, artifact: IndexArtifact): ByteArray {
        // Downloads stay in memory (at most 20 MiB) until verified; only publish() writes files.
        val bytes = fetcher.fetch(URI(trust.indexUrl).resolve(artifact.file).toString(), artifact.size)
        if (bytes.size.toLong() != artifact.size) throw IOException("${artifact.file}: size does not match the index")
        if (sha256(bytes) != artifact.sha256) throw IOException("${artifact.file}: digest does not match the index")
        return bytes
    }

    /** Content-addressed: a file named by its digest is replaced only if its bytes differ. */
    private fun publish(digest: String, bytes: ByteArray) {
        val target = artifacts.resolve("$digest.jar")
        if (Files.isRegularFile(target) && runCatching { sha256(Files.readAllBytes(target)) == digest }.getOrDefault(false)) return
        val temporary = Files.createTempFile(staging, "publish-", ".jar")
        try {
            Files.write(temporary, bytes)
            FileChannel.open(temporary, WRITE).use { it.force(true) }
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun collectUnreferenced(records: List<InstalledRecord>) {
        val referenced = records.flatMap { record -> listOfNotNull(record.current, record.previous) }
            .flatMap { listOfNotNull(it.sha256, it.librarySha256) }.map { "$it.jar" }.toSet()
        Files.list(artifacts).use { files -> files.filter { it.fileName.toString() !in referenced }.forEach(Files::deleteIfExists) }
    }

    private fun commit(record: InstalledRecord) = library.extensionWrite { db ->
        db.execute("""INSERT INTO installed_extensions(pkg,repository,metadata,sha256,previous,library_coordinate,library_sha256,installed_at)
            VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(pkg) DO UPDATE SET repository=excluded.repository, metadata=excluded.metadata,
            sha256=excluded.sha256, previous=excluded.previous, library_coordinate=excluded.library_coordinate,
            library_sha256=excluded.library_sha256, installed_at=excluded.installed_at""",
            record.pkg, record.repository, encode(record.current).toString(), record.current.sha256,
            record.previous?.let { encode(it).toString() }, record.current.libraryCoordinate, record.current.librarySha256,
            System.currentTimeMillis())
    }

    private fun delete(pkg: String) = library.extensionWrite { db -> db.execute("DELETE FROM installed_extensions WHERE pkg=?", pkg) }

    private fun readRepositories(db: Connection) = db.query(
        "SELECT repository,index_url,public_key,sequence,index_sha256,catalog,enabled,builtin FROM extension_repositories ORDER BY builtin DESC, repository",
    ) { row ->
        val catalog = row.getBytes(6)
        RepositoryRecord(
            RepositoryTrust(row.getString(1), row.getString(2), row.getString(3)),
            row.getString(5)?.let { AcceptedIndex(row.getLong(4), it) },
            catalog?.let(ExtensionIndex::parse),
            row.getInt(7) == 1,
            row.getInt(8) == 1,
        )
    }

    private fun readInstalled(db: Connection) = db.query("SELECT repository,metadata,previous FROM installed_extensions ORDER BY pkg") { row ->
        InstalledRecord(row.getString(1), decode(JSONObject(row.getString(2))), row.getString(3)?.let { decode(JSONObject(it)) })
    }

    private fun encode(artifact: InstalledArtifact) = JSONObject()
        .put("pkg", artifact.metadata.pkg).put("sourceClass", artifact.metadata.sourceClass)
        .put("versionCode", artifact.metadata.versionCode).put("versionName", artifact.metadata.versionName)
        .put("sourceVersionCode", artifact.metadata.sourceVersionCode).put("coreApi", artifact.metadata.coreApi)
        .put("hostLibraries", JSONArray(artifact.metadata.hostLibraries))
        .put("name", artifact.name).put("lang", artifact.lang).put("sha256", artifact.sha256)
        .put("libraryCoordinate", artifact.libraryCoordinate ?: JSONObject.NULL)
        .put("librarySha256", artifact.librarySha256 ?: JSONObject.NULL)

    private fun decode(json: JSONObject) = InstalledArtifact(
        ExtensionMetadata(json.getString("pkg"), json.getString("sourceClass"), json.getInt("versionCode"),
            json.getString("versionName"), json.getInt("sourceVersionCode"), json.getString("coreApi"),
            json.getJSONArray("hostLibraries").let { list -> (0 until list.length()).map(list::getString) }),
        json.getString("name"), json.getString("lang"), json.getString("sha256"),
        json.optString("libraryCoordinate").takeIf { !json.isNull("libraryCoordinate") },
        json.optString("librarySha256").takeIf { !json.isNull("librarySha256") },
    )
}
