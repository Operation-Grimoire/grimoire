package io.grimoire.app.data.local.dao

import java.io.ByteArrayOutputStream

internal const val CHAPTER_CONTENT_CHUNK_BYTES = 256 * 1024
internal const val CHAPTER_CONTENT_LENGTH_QUERY = """
    SELECT length(CAST(content AS BLOB)) FROM chapter_content WHERE chapterId = :chapterId
"""
internal const val CHAPTER_CONTENT_CHUNK_QUERY = """
    SELECT substr(CAST(content AS BLOB), :start, :length)
    FROM chapter_content WHERE chapterId = :chapterId
"""
internal const val READ_DOWNLOADS_MISSING_WORD_COUNT_QUERY = """
    SELECT c.id FROM chapters c
    INNER JOIN chapter_content cc ON cc.chapterId = c.id
    WHERE c.read = 1 AND c.wordCount = 0
"""

/** Keep each cursor row small and join bytes before decoding split UTF-8 characters. */
internal suspend fun readChapterContentInChunks(
    byteLength: Long,
    readChunk: suspend (start: Long, length: Int) -> ByteArray?,
): String? {
    val content = ByteArrayOutputStream()
    var start = 1L
    while (start <= byteLength) {
        val chunk = readChunk(start, CHAPTER_CONTENT_CHUNK_BYTES) ?: return null
        if (chunk.isEmpty()) return null
        content.write(chunk)
        start += chunk.size
    }
    return content.toString(Charsets.UTF_8.name())
}
