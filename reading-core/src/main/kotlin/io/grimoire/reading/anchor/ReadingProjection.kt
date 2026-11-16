package io.grimoire.reading.anchor

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.ChapterKey
import java.util.Collections

data class ProjectedPage(val canonicalOrdinal: Int, val page: NovelPage)
data class RenderedTextPosition(val text: String, val offset: Int)

sealed interface ProjectionTarget {
    /** A logical location within an item; the host must realize its text/boundary alignment. */
    data class Item(val renderedIndex: Int, val position: AnchorPosition, val presentationFallback: Boolean) : ProjectionTarget
    data object NoVisibleContent : ProjectionTarget
}

data class ProjectionResolution(val logical: AnchorResolution, val target: ProjectionTarget?)

/** One immutable load's original content and explicit rendered-to-canonical mapping. */
class ReadingProjection private constructor(
    val originalPages: List<NovelPage>,
    val body: List<ProjectedPage>,
    val document: CanonicalDocument?,
    val leadingStart: Boolean,
    val trailingEnd: Boolean,
) {
    private val leadingCount = if (leadingStart) 1 else 0
    val itemCount = body.size + leadingCount + if (trailingEnd) 1 else 0

    /** Provisional observation only. Invalid measurements/keyless/hidden-only content yield no capture. */
    fun capture(renderedIndex: Int, text: RenderedTextPosition? = null): ReadingAnchor? {
        val doc = document ?: return null
        if (renderedIndex !in 0 until itemCount || body.isEmpty()) return null
        val mapped = body.getOrNull(renderedIndex - leadingCount)
        val position = if (mapped == null) {
            if (text != null) return null
            if (leadingStart && renderedIndex == 0) AnchorPosition.Start else AnchorPosition.End
        } else {
            val plain = (mapped.page.content as? PageContent.Text)?.text
            if (text != null && (plain == null || textOffsetError(text.text, text.offset) != null)) return null
            val exact = text != null && text.text == plain
            AnchorPosition.Block(mapped.canonicalOrdinal, doc.blockFingerprint(mapped.canonicalOrdinal),
                if (exact) text!!.offset else 0, if (exact) AnchorPrecision.TEXT_OFFSET else AnchorPrecision.BLOCK_START)
        }
        val anchor = ReadingAnchor(doc.key, doc.logicalRevision, position)
        return anchor.takeIf { resolveReadingAnchor(it, doc) is AnchorResolution.Exact }
    }

    /** Pure restore recommendation; no pixel reuse, persistence or new write authority. */
    fun resolve(anchor: ReadingAnchor): ProjectionResolution? {
        val doc = document ?: return null
        val logical = resolveReadingAnchor(anchor, doc)
        val position = when (logical) {
            is AnchorResolution.Exact -> logical.original.position
            is AnchorResolution.Relocated -> logical.relocated.position
            is AnchorResolution.Unresolved -> logical.fallback
            else -> return ProjectionResolution(logical, null)
        }
        if (body.isEmpty()) return ProjectionResolution(logical, ProjectionTarget.NoVisibleContent)
        val target = when (position) {
            AnchorPosition.Start -> ProjectionTarget.Item(0, position, false)
            AnchorPosition.End -> endTarget(false)
            is AnchorPosition.Block -> {
                val visible = body.indexOfFirst { it.canonicalOrdinal == position.ordinal }
                if (visible >= 0) ProjectionTarget.Item(visible + leadingCount, position, false)
                else {
                    val next = body.indexOfFirst { it.canonicalOrdinal > position.ordinal }
                    if (next < 0) endTarget(true) else {
                        val ordinal = body[next].canonicalOrdinal
                        ProjectionTarget.Item(next + leadingCount, AnchorPosition.Block(ordinal,
                            doc.blockFingerprint(ordinal), 0, AnchorPrecision.BLOCK_START), true)
                    }
                }
            }
        }
        return ProjectionResolution(logical, target)
    }

    private fun endTarget(fallback: Boolean) = ProjectionTarget.Item(itemCount - 1, AnchorPosition.End, fallback)

    companion object {
        fun create(
            key: ChapterKey?, originalPages: List<NovelPage>, body: List<ProjectedPage>,
            leadingStart: Boolean = false, trailingEnd: Boolean = false,
        ): ReadingProjection {
            val raw = originalPages.toList()
            val mapped = body.toList()
            var previous = -1
            for (entry in mapped) {
                require(entry.canonicalOrdinal > previous && entry.canonicalOrdinal < raw.size) { "Invalid projection ordinal/order" }
                val source = raw[entry.canonicalOrdinal].content
                val rendered = entry.page.content
                require(when (source) {
                    is PageContent.Image -> rendered is PageContent.Image
                    is PageContent.Separator -> rendered is PageContent.Separator
                    is PageContent.Text -> source == rendered
                }) {
                    "Projection may replace image locators, not canonical prose or block types"
                }
                previous = entry.canonicalOrdinal
            }
            val decorations = (if (leadingStart) 1 else 0) + if (trailingEnd) 1 else 0
            require(mapped.size <= Int.MAX_VALUE - decorations) { "Projection item count overflow" }
            return ReadingProjection(Collections.unmodifiableList(raw), Collections.unmodifiableList(mapped),
                key?.let { CanonicalDocument.fromPages(it, raw) }, leadingStart, trailingEnd)
        }
    }
}
