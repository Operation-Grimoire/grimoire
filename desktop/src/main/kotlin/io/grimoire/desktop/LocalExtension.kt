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

/** Operator-selected trusted local artifacts. This is not remote trust or a plugin sandbox. */
class LocalExtension private constructor(
    val source: Source,
    val packageName: String,
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
        fun open(manifest: Path, hostApi: String): LocalExtension {
            val config = Properties().apply { Files.newInputStream(manifest).use(::load) }
            fun required(key: String): String = config.getProperty(key)?.takeIf { it.isNotBlank() }
                ?: error("Missing local manifest field: $key")
            val root = manifest.toAbsolutePath().parent
            fun bytes(prefix: String): ByteArray {
                val path = root.resolve(required("$prefix.file"))
                require(Files.isRegularFile(path)) { "Invalid JAR: $path" }
                val limit = 20 * 1024 * 1024
                val content = Files.newInputStream(path).use { it.readNBytes(limit + 1) }
                require(content.size <= limit) { "JAR exceeds 20 MiB: $path" }
                val expected = required("$prefix.sha256")
                require(expected.matches(Regex("[0-9a-f]{64}")) && sha256(content) == expected) {
                    "JAR digest does not match the trusted local manifest: $path"
                }
                return content
            }
            val sourceBytes = bytes("source")
            val pkg = required("source.package")
            val entry = required("source.class")
            require(pkg.matches(Regex("io\\.grimoire\\.extension(?:\\.[a-zA-Z_][a-zA-Z_0-9]*)+"))) { "Invalid source package" }
            require(entry.startsWith("$pkg.") && entry.matches(Regex("[a-zA-Z_0-9.$]+"))) { "Invalid entry point" }
            val attributes = inspect(sourceBytes, pkg.replace('.', '/') + "/")
            val expected = mapOf(
                "Grimoire-Extension-Format" to "1",
                "Grimoire-Source-Package" to pkg,
                "Grimoire-Source-Class" to entry,
                "Grimoire-Version-Code" to required("source.versionCode"),
                "Grimoire-Version-Name" to required("source.versionName"),
                "Grimoire-Source-Version-Code" to required("source.sourceVersionCode"),
                "Grimoire-Core-Api" to required("source.coreApi"),
            )
            expected.forEach { (key, value) -> require(attributes.getValue(key) == value) { "JAR metadata mismatch: $key" } }
            val minimum = attributes.getValue("Grimoire-Core-Api")
            require(declaredExtensionApi(minimum) != null && supportsExtensionApi(minimum, hostApi)) {
                "Extension requires API $minimum; host provides $hostApi"
            }
            require(required("source.versionCode").toIntOrNull()?.let { it > 0 } == true)
            val sourceVersion = required("source.sourceVersionCode").toIntOrNull()
            require(sourceVersion != null && sourceVersion > 0)
            val requiredLibraries = attributes.getValue("Grimoire-Host-Libraries")?.split(',')
                ?.map(String::trim)?.filter(String::isNotEmpty) ?: error("Missing Grimoire-Host-Libraries")
            val libraryBytes = if (config.containsKey("library.file")) bytes("library") else null
            val libraryCoordinate = if (libraryBytes != null) required("library.coordinate") else null
            require(libraryCoordinate == null || libraryCoordinate.matches(Regex("io\\.grimoire:extensions-lib:[0-9][a-zA-Z0-9.\\-]*")))
            require(requiredLibraries.all { it == HostRuntime.jsonCoordinate || it == libraryCoordinate }) {
                "Unsupported host libraries: ${requiredLibraries.joinToString()}"
            }
            require(libraryBytes == null || libraryCoordinate in requiredLibraries) { "Undeclared host library: $libraryCoordinate" }
            libraryBytes?.let { inspect(it, "io/grimoire/extensions/lib/") }

            // Load verified snapshots, never a path that can change after verification.
            val directory = Files.createTempDirectory("grimoire-extension-")
            val files = mutableListOf<Path>()
            var loader: URLClassLoader? = null
            try {
                for ((index, content) in listOfNotNull(sourceBytes, libraryBytes).withIndex()) {
                    files.add(directory.resolve("$index.jar").also { Files.write(it, content) })
                }
                loader = URLClassLoader(files.map { it.toUri().toURL() }.toTypedArray(), Source::class.java.classLoader)
                val type = Class.forName(entry, false, loader)
                require(type.classLoader === loader && Source::class.java.isAssignableFrom(type)) { "Invalid source class" }
                val info = type.getAnnotation(SourceInfo::class.java) ?: error("SourceInfo is missing")
                require(info.versionCode == sourceVersion) { "SourceInfo version mismatch" }
                val source = type.getDeclaredConstructor().newInstance() as Source
                require(info.name == source.name && info.lang == source.lang) { "SourceInfo identity mismatch" }
                return LocalExtension(source, pkg, loader, files, directory)
            } catch (failure: Throwable) {
                // Keep the original load failure while attempting every cleanup operation.
                runCatching { loader?.close() }.exceptionOrNull()?.let(failure::addSuppressed)
                files.forEach { file -> runCatching { Files.deleteIfExists(file) }.exceptionOrNull()?.let(failure::addSuppressed) }
                runCatching { Files.deleteIfExists(directory) }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
        }

        private fun inspect(bytes: ByteArray, namespace: String): java.util.jar.Attributes =
            JarInputStream(bytes.inputStream()).use { jar ->
                val attributes = jar.manifest?.mainAttributes ?: error("JAR manifest is missing")
                require(attributes.getValue("Class-Path") == null) { "External JAR Class-Path is unsupported" }
                val entries = mutableSetOf<String>()
                while (true) {
                    val entry = jar.nextJarEntry ?: break
                    require(!entry.name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) { "Duplicate JAR manifest" }
                    require(entries.add(entry.name)) { "Duplicate JAR entry" }
                    require(!entry.name.endsWith(".class") || entry.name.startsWith(namespace)) { "JAR contains host or foreign classes" }
                }
                attributes
            }
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
