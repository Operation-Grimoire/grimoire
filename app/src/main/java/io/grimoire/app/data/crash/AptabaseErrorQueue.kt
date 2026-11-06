package io.grimoire.app.data.crash

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.Duration
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Small, synchronous crash journal. Each report is committed before the dying process exits. */
internal class AptabaseErrorQueue(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun add(report: AptabaseErrorReport) {
        directory.mkdirs()
        pending() // Prune expired, oversized and incomplete records first.
        val destination = File(directory, "${UUID.randomUUID()}.json")
        val temporary = File(directory, "${destination.name}.tmp")
        val bytes = json.encodeToString(report).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        try {
            FileOutputStream(temporary).use { stream -> stream.write(bytes); stream.fd.sync() }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
        files().dropLast(MAX_REPORTS).forEach { it.delete() }
    }

    @Synchronized
    fun pending(now: Instant = Instant.now()): List<Pair<String, AptabaseErrorReport>> =
        directory.listFiles().orEmpty().sortedBy { it.lastModified() }.mapNotNull { file ->
            val report = if (file.extension == "json" && file.length() <= MAX_BYTES) {
                runCatching { json.decodeFromString<AptabaseErrorReport>(file.readText()) }.getOrNull()
            } else null
            val captured = report?.let { runCatching { Instant.parse(it.timestamp) }.getOrNull() }
            if (report == null || captured == null || captured.isBefore(now.minus(RETENTION)) || captured.isAfter(now.plusSeconds(300))) {
                file.delete()
                null
            } else file.name to report
        }.takeLast(MAX_REPORTS)

    @Synchronized
    fun remove(id: String) {
        require(id == File(id).name && id.endsWith(".json"))
        File(directory, id).delete()
    }

    @Synchronized
    fun clear() { directory.listFiles().orEmpty().forEach { it.delete() } }

    private fun files() = directory.listFiles().orEmpty().filter { it.extension == "json" }.sortedBy { it.lastModified() }

    companion object {
        const val MAX_REPORTS = 8
        private const val MAX_BYTES = 64 * 1024
        private val RETENTION = Duration.ofDays(14)
    }
}

/** No capture until the persisted preference has loaded; one fatal report per process. */
internal class AptabaseCrashCapture(
    private val queue: AptabaseErrorQueue,
    private val configured: Boolean,
) {
    private var enabled = false
    private var captured = false

    @Synchronized
    fun setEnabled(value: Boolean) { enabled = value }

    @Synchronized
    fun capture(build: () -> AptabaseErrorReport) {
        if (!enabled || !configured || captured) return
        queue.add(build())
        captured = true
    }
}
