package io.grimoire.desktop

import io.grimoire.api.source.Source
import io.grimoire.api.source.SourceInfo
import io.grimoire.api.source.sourceIdFor
import io.grimoire.reading.declaredExtensionApi
import io.grimoire.reading.supportsExtensionApi
import java.io.Closeable
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import java.util.jar.JarInputStream

/** Identity a JAR's `Grimoire-*` manifest must match before any of its classes load. */
data class ExtensionMetadata(
    val pkg: String,
    val sourceClass: String,
    val versionCode: Int,
    val versionName: String,
    val sourceVersionCode: Int,
    val coreApi: String,
    val hostLibraries: List<String>,
)

/** A library JAR offered to an extension, already bound to a digest by the caller. */
class LibraryArtifact(val coordinate: String, val bytes: ByteArray)

/** Verified extension code in its own class loader. This is not a plugin sandbox. */
class LocalExtension private constructor(
    val source: Source,
    val packageName: String,
    val versionName: String,
    private val loader: URLClassLoader,
    private val snapshots: List<Path>,
    private val directory: Path,
) : Closeable {
    val sourceId: Long = sourceIdFor(packageName)

    override fun close() {
        try { loader.close() } finally {
            snapshots.forEach { Files.deleteIfExists(it) }
            Files.deleteIfExists(directory)
        }
    }

    companion object {
        /** Operator-selected trusted local manifest, for development. */
        fun open(manifest: Path, hostApi: String): LocalExtension {
            val config = Properties().apply { Files.newInputStream(manifest).use(::load) }
            fun required(key: String): String = config.getProperty(key)?.takeIf { it.isNotBlank() }
                ?: error("Missing local manifest field: $key")
            val root = manifest.toAbsolutePath().parent
            fun bytes(prefix: String): ByteArray {
                val path = root.resolve(required("$prefix.file"))
                require(Files.isRegularFile(path)) { "Invalid JAR: $path" }
                val limit = ExtensionIndex.MAX_ARTIFACT_BYTES.toInt()
                val content = Files.newInputStream(path).use { it.readNBytes(limit + 1) }
                require(content.size <= limit) { "JAR exceeds 20 MiB: $path" }
                val expected = required("$prefix.sha256")
                require(expected.matches(Regex("[0-9a-f]{64}")) && sha256(content) == expected) {
                    "JAR digest does not match the trusted local manifest: $path"
                }
                return content
            }
            val sourceBytes = bytes("source")
            val metadata = ExtensionMetadata(
                pkg = required("source.package"),
                sourceClass = required("source.class"),
                versionCode = required("source.versionCode").toIntOrNull() ?: error("Invalid source.versionCode"),
                versionName = required("source.versionName"),
                sourceVersionCode = required("source.sourceVersionCode").toIntOrNull() ?: error("Invalid source.sourceVersionCode"),
                coreApi = required("source.coreApi"),
                hostLibraries = hostLibraries(sourceBytes),
            )
            val library = if (config.containsKey("library.file")) LibraryArtifact(required("library.coordinate"), bytes("library")) else null
            return load(sourceBytes, metadata, library, hostApi)
        }

        /** Checks [bytes] against [expected] and the host without loading any class. */
        fun verify(bytes: ByteArray, expected: ExtensionMetadata, library: LibraryArtifact?, hostApi: String) {
            val pkg = expected.pkg
            require(pkg.matches(Regex("io\\.grimoire\\.extension(?:\\.[a-zA-Z_][a-zA-Z_0-9]*)+"))) { "Invalid source package" }
            require(expected.sourceClass.startsWith("$pkg.") && expected.sourceClass.matches(Regex("[a-zA-Z_0-9.$]+"))) { "Invalid entry point" }
            require(expected.versionCode > 0) { "Invalid versionCode" }
            require(expected.sourceVersionCode > 0) { "Invalid sourceVersionCode" }
            val attributes = inspect(bytes, pkg.replace('.', '/') + "/")
            val manifest = mapOf(
                "Grimoire-Extension-Format" to "1",
                "Grimoire-Source-Package" to pkg,
                "Grimoire-Source-Class" to expected.sourceClass,
                "Grimoire-Version-Code" to expected.versionCode.toString(),
                "Grimoire-Version-Name" to expected.versionName,
                "Grimoire-Source-Version-Code" to expected.sourceVersionCode.toString(),
                "Grimoire-Core-Api" to expected.coreApi,
            )
            manifest.forEach { (key, value) -> require(attributes.getValue(key) == value) { "JAR metadata mismatch: $key" } }
            val minimum = expected.coreApi
            require(declaredExtensionApi(minimum) != null && supportsExtensionApi(minimum, hostApi)) {
                "Extension requires API $minimum; host provides $hostApi"
            }
            val required = hostLibraries(attributes)
            require(required.toSet() == expected.hostLibraries.toSet()) { "JAR metadata mismatch: Grimoire-Host-Libraries" }
            val coordinate = library?.coordinate
            require(coordinate == null || coordinate.matches(Regex("io\\.grimoire:extensions-lib:[0-9][a-zA-Z0-9.\\-]*"))) { "Unsupported library coordinate" }
            require(required.all { it == HostRuntime.jsonCoordinate || it == coordinate }) {
                "Unsupported host libraries: ${required.joinToString()}"
            }
            require(library == null || coordinate in required) { "Undeclared host library: $coordinate" }
            library?.let { inspect(it.bytes, "io/grimoire/extensions/lib/") }
        }

        fun load(sourceBytes: ByteArray, expected: ExtensionMetadata, library: LibraryArtifact?, hostApi: String): LocalExtension {
            verify(sourceBytes, expected, library, hostApi)
            // Load verified snapshots, never a path that can change after verification.
            val directory = Files.createTempDirectory("grimoire-extension-")
            val files = mutableListOf<Path>()
            var loader: URLClassLoader? = null
            try {
                for ((index, content) in listOfNotNull(sourceBytes, library?.bytes).withIndex()) {
                    files.add(directory.resolve("$index.jar").also { Files.write(it, content) })
                }
                loader = URLClassLoader(files.map { it.toUri().toURL() }.toTypedArray(), Source::class.java.classLoader)
                val type = Class.forName(expected.sourceClass, false, loader)
                require(type.classLoader === loader && Source::class.java.isAssignableFrom(type)) { "Invalid source class" }
                val info = type.getAnnotation(SourceInfo::class.java) ?: error("SourceInfo is missing")
                require(info.versionCode == expected.sourceVersionCode) { "SourceInfo version mismatch" }
                val source = type.getDeclaredConstructor().newInstance() as Source
                require(info.name == source.name && info.lang == source.lang) { "SourceInfo identity mismatch" }
                return LocalExtension(source, expected.pkg, expected.versionName, loader, files, directory)
            } catch (failure: Throwable) {
                // Keep the original load failure while attempting every cleanup operation.
                runCatching { loader?.close() }.exceptionOrNull()?.let(failure::addSuppressed)
                files.forEach { file -> runCatching { Files.deleteIfExists(file) }.exceptionOrNull()?.let(failure::addSuppressed) }
                runCatching { Files.deleteIfExists(directory) }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
        }

        private fun hostLibraries(bytes: ByteArray) = hostLibraries(inspect(bytes, null))

        private fun hostLibraries(attributes: java.util.jar.Attributes) =
            attributes.getValue("Grimoire-Host-Libraries")?.split(',')
                ?.map(String::trim)?.filter(String::isNotEmpty) ?: error("Missing Grimoire-Host-Libraries")

        private fun inspect(bytes: ByteArray, namespace: String?): java.util.jar.Attributes =
            JarInputStream(bytes.inputStream()).use { jar ->
                val attributes = jar.manifest?.mainAttributes ?: error("JAR manifest is missing")
                require(attributes.getValue("Class-Path") == null) { "External JAR Class-Path is unsupported" }
                val entries = mutableSetOf<String>()
                while (true) {
                    val entry = jar.nextJarEntry ?: break
                    require(!entry.name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) { "Duplicate JAR manifest" }
                    require(entries.add(entry.name)) { "Duplicate JAR entry" }
                    require(namespace == null || !entry.name.endsWith(".class") || entry.name.startsWith(namespace)) { "JAR contains host or foreign classes" }
                }
                attributes
            }
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
