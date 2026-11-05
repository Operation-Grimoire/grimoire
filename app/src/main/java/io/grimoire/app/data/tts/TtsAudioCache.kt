package io.grimoire.app.data.tts

import java.io.File
import java.security.MessageDigest

/** Optional replay cache. Credentials are never part of its keys or files. */
internal class TtsAudioCache(
    private val directory: File,
    private val maxBytes: Long = 32L * 1024 * 1024,
    private val maxEntries: Int = 128,
) {
    private var lastTouch = 0L

    @Synchronized
    fun get(key: String): ByteArray? = runCatching {
        val file = File(directory, "$key.mp3")
        if (!file.isFile) return@runCatching null
        file.readBytes().also { touch(file) }
    }.getOrNull()

    @Synchronized
    fun put(key: String, bytes: ByteArray) {
        runCatching {
            directory.mkdirs()
            if (bytes.size > maxBytes) return
            val file = File(directory, "$key.mp3")
            val temporary = File.createTempFile("audio-", ".tmp", directory)
            try {
                temporary.writeBytes(bytes)
                file.delete()
                check(temporary.renameTo(file))
                touch(file)
                trim()
            } finally {
                temporary.delete()
            }
        }
    }

    private fun touch(file: File) {
        lastTouch = maxOf(System.currentTimeMillis(), lastTouch + 1)
        file.setLastModified(lastTouch)
    }

    private fun trim() {
        val files = directory.listFiles { file -> file.extension == "mp3" }.orEmpty()
            .sortedBy { it.lastModified() }
        var size = files.sumOf { it.length() }
        var count = files.size
        for (file in files) {
            if (size <= maxBytes && count <= maxEntries) break
            val length = file.length()
            if (file.delete()) { size -= length; count-- }
        }
    }
}

internal fun ttsAudioCacheKey(config: TtsEngineConfig, voiceId: String, text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    listOf("mp3-v1", voiceId, config.elevenLabsModel, config.rate.toString(),
        config.pitch.toString(), config.localeTag.orEmpty(), text).forEach { part ->
        val bytes = part.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
