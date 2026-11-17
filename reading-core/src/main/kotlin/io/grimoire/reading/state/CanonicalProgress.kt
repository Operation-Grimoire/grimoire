package io.grimoire.reading.state

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.anchor.*

const val CANONICAL_PROGRESS_VERSION = 1

enum class ProgressPrecision { BOUNDARY, BLOCK_START, TEXT_OFFSET }

/** Read-only measure of current location, never completion, a watermark or write authority. */
class CanonicalProgress internal constructor(
    val key: ChapterKey, val logicalRevision: String, val unitCount: Int,
    val numerator: Long, val denominator: Long, val precision: ProgressPrecision,
) {
    val modelVersion: Int get() = CANONICAL_PROGRESS_VERSION
    /** Approximate presentation value. The rational basis retains exact boundaries. */
    val fraction: Double get() = numerator.toDouble() / denominator
}

data class MeasuredReadingPosition(val resolution: AnchorResolution, val measure: CanonicalProgress?)

internal fun AnchorBlock.hasProgressUnit(): Boolean = when (this) {
    is AnchorBlock.Text -> text.isNotBlank()
    is AnchorBlock.Image -> true
    AnchorBlock.Separator -> false
}

/** Products of two nonnegative Int-sized counts fit in Long; retain the exact, unreduced basis. */
internal fun canonicalPositionRatio(before: Int, total: Int, offset: Int = 0, length: Int = 1): Pair<Long, Long> {
    require(total > 0 && before in 0..total && length > 0 && offset in 0..length)
    require(before < total || offset == 0)
    return (before.toLong() * length + offset) to (total.toLong() * length)
}

/** Resolution can recommend a relocated measure, but does not capture or persist a replacement anchor. */
fun measureReadingPosition(anchor: ReadingAnchor, document: CanonicalDocument): MeasuredReadingPosition {
    val resolution = resolveReadingAnchor(anchor, document)
    val resolved = when (resolution) {
        is AnchorResolution.Exact -> resolution.original
        is AnchorResolution.Relocated -> resolution.relocated
        else -> return MeasuredReadingPosition(resolution, null)
    }
    val total = document.blocks.count { it.hasProgressUnit() }
    if (total == 0) return MeasuredReadingPosition(resolution, null)
    var precision = ProgressPrecision.BOUNDARY
    val ratio = when (val position = resolved.position) {
        AnchorPosition.Start -> canonicalPositionRatio(0, total)
        AnchorPosition.End -> canonicalPositionRatio(total, total)
        is AnchorPosition.Block -> {
            val before = document.blocks.subList(0, position.ordinal).count { it.hasProgressUnit() }
            val block = document.blocks[position.ordinal]
            if (block.hasProgressUnit()) {
                precision = if (position.precision == AnchorPrecision.TEXT_OFFSET) ProgressPrecision.TEXT_OFFSET else ProgressPrecision.BLOCK_START
            }
            if (precision == ProgressPrecision.TEXT_OFFSET && block is AnchorBlock.Text) {
                canonicalPositionRatio(before, total, position.textOffset, block.text.length)
            } else canonicalPositionRatio(before, total)
        }
    }
    return MeasuredReadingPosition(resolution, CanonicalProgress(document.key, document.logicalRevision, total,
        ratio.first, ratio.second, precision))
}
