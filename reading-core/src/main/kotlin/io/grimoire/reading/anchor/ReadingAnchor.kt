package io.grimoire.reading.anchor

import io.grimoire.reading.ChapterKey

const val READING_ANCHOR_VERSION = 1

enum class AnchorPrecision { TEXT_OFFSET, BLOCK_START }

sealed interface AnchorPosition {
    data object Start : AnchorPosition
    data object End : AnchorPosition
    data class Block(
        val ordinal: Int,
        val fingerprint: String,
        val textOffset: Int,
        val precision: AnchorPrecision,
    ) : AnchorPosition
}

/** Raw values can represent invalid/unsupported persisted evidence for non-destructive resolution. */
data class ReadingAnchor(
    val key: ChapterKey,
    val logicalRevision: String,
    val position: AnchorPosition,
    val modelVersion: Int = READING_ANCHOR_VERSION,
)

enum class InvalidAnchorReason {
    LOGICAL_REVISION, BLOCK_FINGERPRINT, NEGATIVE_ORDINAL, NEGATIVE_OFFSET,
    PRECISION, ORDINAL_OUT_OF_RANGE, BLOCK_MISMATCH, OFFSET_OUT_OF_RANGE, SPLIT_SURROGATE_PAIR,
}

enum class UnresolvedAnchorReason { CONTENT_CHANGED, AMBIGUOUS_BLOCK }

sealed interface AnchorResolution {
    val original: ReadingAnchor

    data class Exact(override val original: ReadingAnchor) : AnchorResolution
    data class Relocated(override val original: ReadingAnchor, val relocated: ReadingAnchor) : AnchorResolution
    data class Unresolved(override val original: ReadingAnchor, val reason: UnresolvedAnchorReason) : AnchorResolution {
        /** Viewport recommendation only. It is not a replacement checkpoint. */
        val fallback: AnchorPosition = AnchorPosition.Start
    }
    data class IdentityMismatch(override val original: ReadingAnchor, val expectedKey: ChapterKey) : AnchorResolution
    data class UnsupportedVersion(override val original: ReadingAnchor) : AnchorResolution
    data class Invalid(override val original: ReadingAnchor, val reason: InvalidAnchorReason) : AnchorResolution
    data class NoContent(override val original: ReadingAnchor) : AnchorResolution
}

private val fingerprintFormat = Regex("[a-f0-9]{64}")

/** Pure logical resolution. Rendering, legacy hints, storage guards and persistence remain separate. */
fun resolveReadingAnchor(anchor: ReadingAnchor, document: CanonicalDocument): AnchorResolution {
    if (anchor.key != document.key) return AnchorResolution.IdentityMismatch(anchor, document.key)
    if (anchor.modelVersion != READING_ANCHOR_VERSION) return AnchorResolution.UnsupportedVersion(anchor)
    fun invalid(reason: InvalidAnchorReason) = AnchorResolution.Invalid(anchor, reason)
    if (!fingerprintFormat.matches(anchor.logicalRevision)) return invalid(InvalidAnchorReason.LOGICAL_REVISION)
    val position = anchor.position
    if (position is AnchorPosition.Block) {
        if (!fingerprintFormat.matches(position.fingerprint)) return invalid(InvalidAnchorReason.BLOCK_FINGERPRINT)
        if (position.ordinal < 0) return invalid(InvalidAnchorReason.NEGATIVE_ORDINAL)
        if (position.textOffset < 0) return invalid(InvalidAnchorReason.NEGATIVE_OFFSET)
        if (position.precision == AnchorPrecision.BLOCK_START && position.textOffset != 0) return invalid(InvalidAnchorReason.PRECISION)
    }
    if (document.blocks.isEmpty()) return AnchorResolution.NoContent(anchor)

    if (anchor.logicalRevision == document.logicalRevision) {
        if (position is AnchorPosition.Block) {
            val block = document.blocks.getOrNull(position.ordinal) ?: return invalid(InvalidAnchorReason.ORDINAL_OUT_OF_RANGE)
            if (document.blockFingerprint(position.ordinal) != position.fingerprint) return invalid(InvalidAnchorReason.BLOCK_MISMATCH)
            validateOffset(position, block)?.let { return invalid(it) }
        }
        return AnchorResolution.Exact(anchor)
    }

    if (position !is AnchorPosition.Block) return AnchorResolution.Unresolved(anchor, UnresolvedAnchorReason.CONTENT_CHANGED)
    var found = -1
    for (ordinal in document.blocks.indices) {
        if (document.blockFingerprint(ordinal) != position.fingerprint) continue
        if (found >= 0) return AnchorResolution.Unresolved(anchor, UnresolvedAnchorReason.AMBIGUOUS_BLOCK)
        found = ordinal
    }
    if (found < 0) return AnchorResolution.Unresolved(anchor, UnresolvedAnchorReason.CONTENT_CHANGED)
    validateOffset(position, document.blocks[found])?.let { return invalid(it) }
    val relocated = anchor.copy(logicalRevision = document.logicalRevision, position = position.copy(ordinal = found))
    return AnchorResolution.Relocated(anchor, relocated)
}

private fun validateOffset(position: AnchorPosition.Block, block: AnchorBlock): InvalidAnchorReason? {
    if (block !is AnchorBlock.Text) {
        return if (position.precision == AnchorPrecision.BLOCK_START) null else InvalidAnchorReason.PRECISION
    }
    val offset = position.textOffset
    if (offset > block.text.length) return InvalidAnchorReason.OFFSET_OUT_OF_RANGE
    if (offset > 0 && offset < block.text.length &&
        block.text[offset - 1].isHighSurrogate() && block.text[offset].isLowSurrogate()) {
        return InvalidAnchorReason.SPLIT_SURROGATE_PAIR
    }
    return null
}
