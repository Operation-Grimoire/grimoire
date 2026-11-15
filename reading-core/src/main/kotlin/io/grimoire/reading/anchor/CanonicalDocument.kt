package io.grimoire.reading.anchor

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.ChapterKey
import java.security.MessageDigest
import java.util.Collections

/** Original content before renderer filtering or cached-image URI replacement. */
sealed interface AnchorBlock {
    data class Text(val text: String) : AnchorBlock
    data class Image(val sourceUrl: String) : AnchorBlock
    data object Separator : AnchorBlock
}

/** Immutable semantic snapshot; neither a storage generation nor permission to write. */
class CanonicalDocument private constructor(
    val key: ChapterKey,
    val blocks: List<AnchorBlock>,
    val logicalRevision: String,
    private val blockFingerprints: List<String>,
) {
    fun blockFingerprint(ordinal: Int): String = blockFingerprints[ordinal]

    companion object {
        fun fromPages(key: ChapterKey, pages: List<NovelPage>): CanonicalDocument {
            // Copy structure and retain immutable strings, not source page indices or HTML.
            val blocks = pages.map { page -> when (val content = page.content) {
                is PageContent.Text -> AnchorBlock.Text(content.text)
                is PageContent.Image -> AnchorBlock.Image(content.url)
                is PageContent.Separator -> AnchorBlock.Separator
            } }
            val framing = AnchorFraming()
            val document = framing.digest("grimoire-reading-document")
            framing.integer(document, blocks.size)
            val fingerprints = blocks.map { block ->
                val individual = framing.digest("grimoire-reading-block")
                framing.block(document, block)
                framing.block(individual, block)
                framing.hex(individual)
            }
            return CanonicalDocument(key, Collections.unmodifiableList(blocks), framing.hex(document),
                Collections.unmodifiableList(fingerprints))
        }
    }
}

/** Contract v1: unsigned big-endian framing of exact UTF-16 code units, without a BOM. */
private class AnchorFraming {
    private val buffer = ByteArray(4096)

    fun digest(domain: String): MessageDigest = MessageDigest.getInstance("SHA-256").also {
        it.update(domain.toByteArray(Charsets.US_ASCII))
        it.update(0.toByte())
        integer(it, READING_ANCHOR_VERSION)
    }

    fun integer(digest: MessageDigest, value: Int) {
        require(value >= 0) { "Anchor framing length overflow" }
        for (shift in 24 downTo 0 step 8) digest.update((value ushr shift).toByte())
    }

    fun block(digest: MessageDigest, block: AnchorBlock) {
        when (block) {
            is AnchorBlock.Text -> { digest.update(0.toByte()); string(digest, block.text) }
            AnchorBlock.Separator -> digest.update(1.toByte())
            is AnchorBlock.Image -> { digest.update(2.toByte()); string(digest, block.sourceUrl) }
        }
    }

    private fun string(digest: MessageDigest, value: String) {
        integer(digest, value.length)
        var position = 0
        while (position < value.length) {
            val units = minOf(value.length - position, buffer.size / 2)
            repeat(units) { index ->
                val unit = value[position + index].code
                buffer[index * 2] = (unit ushr 8).toByte()
                buffer[index * 2 + 1] = unit.toByte()
            }
            digest.update(buffer, 0, units * 2)
            position += units
        }
    }

    fun hex(digest: MessageDigest): String = buildString(64) {
        val hex = "0123456789abcdef"
        for (byte in digest.digest()) {
            val value = byte.toInt() and 255
            append(hex[value ushr 4]); append(hex[value and 15])
        }
    }
}
