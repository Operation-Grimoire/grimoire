package io.grimoire.reading.anchor

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.*
import org.junit.Assert.*
import org.junit.Test

class ReadingProjectionTest {
    private val key = ChapterKey(NovelKey(17, "/novel"), "/chapter")
    private val raw = listOf(NovelPage(99, PageContent.Text("")), NovelPage(99, PageContent.Text("   ")),
        NovelPage(99, PageContent.Text("The harbor bell rang.", "<b>different rendering</b>")),
        NovelPage(99, PageContent.Image("remote")), NovelPage(99, PageContent.Separator()),
        NovelPage(99, PageContent.Text("Mira followed the light.")))

    @Test fun androidAndDesktopIndicesCaptureTheSameCanonicalBlock() {
        val phone = projection(listOf(2, 3, 4, 5), true)
        val desktop = projection(raw.indices.toList())
        assertEquals(phone.capture(1), desktop.capture(2))
        val block = phone.capture(1)!!.position as AnchorPosition.Block
        assertEquals(2, block.ordinal)
        assertEquals(AnchorPrecision.BLOCK_START, block.precision)
        assertEquals(0, block.textOffset)
        assertEquals(AnchorPosition.Start, phone.capture(0)!!.position)
        assertEquals(AnchorPosition.End, phone.capture(5)!!.position)
    }

    @Test fun onlyFaithfulRenderedPlainTextCanCaptureATextOffset() {
        val p = projection(listOf(2), true)
        val exact = p.capture(1, RenderedTextPosition("The harbor bell rang.", 7))!!
        assertEquals(7, (exact.position as AnchorPosition.Block).textOffset)
        assertEquals(AnchorPrecision.TEXT_OFFSET, exact.position.precision)
        val coarse = p.capture(1, RenderedTextPosition("different rendering", 3))!!
        assertEquals(AnchorPrecision.BLOCK_START, (coarse.position as AnchorPosition.Block).precision)
        assertEquals(0, coarse.position.textOffset)
        assertNull(p.capture(1, RenderedTextPosition("x", 2)))
        assertNull(p.capture(0, RenderedTextPosition("title", 0)))
    }

    @Test fun captureRejectsInvalidCoordinatesAndSplitPairsIncludingUnmappableText() {
        val pages = listOf(NovelPage(0, PageContent.Text("A\uD83D\uDE00")), NovelPage(1, PageContent.Image("url")))
        val p = ReadingProjection.create(key, pages, pages.mapIndexed(::ProjectedPage))
        for (index in listOf(-1, 2, Int.MAX_VALUE)) assertNull(p.capture(index))
        for (offset in listOf(-1, 2, 4)) assertNull(p.capture(0, RenderedTextPosition("A\uD83D\uDE00", offset)))
        assertNotNull(p.capture(0, RenderedTextPosition("A\uD83D\uDE00", 3)))
        assertNull(p.capture(0, RenderedTextPosition("\uD83D\uDE00", 1)))
        assertNull(p.capture(1, RenderedTextPosition("image", 0)))
        assertEquals(AnchorPrecision.BLOCK_START, (p.capture(1)!!.position as AnchorPosition.Block).precision)
    }

    @Test fun imageProjectionRetainsOriginalFingerprintAndImmutableSources() {
        val original = raw.toMutableList()
        val entries = mutableListOf(ProjectedPage(2, raw[2]), ProjectedPage(3, raw[3].copy(content = PageContent.Image("file:///cache"))))
        val p = ReadingProjection.create(key, original, entries)
        val expected = CanonicalDocument.fromPages(key, raw).logicalRevision
        original.clear(); entries.clear()
        assertEquals(6, p.originalPages.size)
        assertEquals(2, p.body.size)
        assertEquals(AnchorBlock.Image("remote"), p.document!!.blocks[3])
        assertEquals(expected, p.document.logicalRevision)
        assertEquals(PageContent.Image("file:///cache"), p.body[1].page.content)
        for (list in listOf(p.originalPages, p.body)) {
            try { (list as MutableList<*>).clear(); fail("Mutable projection") } catch (_: UnsupportedOperationException) { }
        }
    }

    @Test fun factoryRejectsFalseMappingsAndProseOrTypeReplacement() {
        val invalid = listOf(listOf(ProjectedPage(-1, raw[2])), listOf(ProjectedPage(6, raw[2])),
            listOf(ProjectedPage(2, raw[2]), ProjectedPage(2, raw[2])),
            listOf(ProjectedPage(3, raw[3]), ProjectedPage(2, raw[2])),
            listOf(ProjectedPage(2, raw[2].copy(content = PageContent.Text("other")))),
            listOf(ProjectedPage(2, raw[2].copy(content = PageContent.Image("remote")))),
            listOf(ProjectedPage(3, raw[2])))
        for (entries in invalid) {
            try { ReadingProjection.create(key, raw, entries); fail("Accepted false mapping") } catch (_: IllegalArgumentException) { }
        }
        val separator = ReadingProjection.create(key, raw, listOf(ProjectedPage(4, NovelPage(45, PageContent.Separator()))))
        assertEquals(4, (separator.capture(0)!!.position as AnchorPosition.Block).ordinal)
    }

    @Test fun hiddenBlockTargetsTheNextVisibleBlockOrEndWithoutReplacingLogicalEvidence() {
        val all = projection(raw.indices.toList())
        val p = projection(listOf(2, 3), true)
        val hidden = all.capture(0)!!
        val result = p.resolve(hidden)!!
        assertEquals(AnchorResolution.Exact(hidden), result.logical)
        val target = result.target as ProjectionTarget.Item
        assertEquals(1, target.renderedIndex)
        assertEquals(2, (target.position as AnchorPosition.Block).ordinal)
        assertEquals(AnchorPrecision.BLOCK_START, target.position.precision)
        assertTrue(target.presentationFallback)
        val tail = all.capture(5)!!
        val tailResult = p.resolve(tail)!!
        assertSame(tail, tailResult.logical.original)
        assertEquals(ProjectionTarget.Item(3, AnchorPosition.End, true), tailResult.target)
    }

    @Test fun noVisibleContentAndEmptyContentAreDifferentAndNeverCaptureDecorations() {
        val hidden = ReadingProjection.create(key, raw, emptyList(), true, true)
        val a = projection(raw.indices.toList()).capture(2)!!
        assertEquals(ProjectionTarget.NoVisibleContent, hidden.resolve(a)!!.target)
        assertNull(hidden.capture(0)); assertNull(hidden.capture(1))
        val empty = ReadingProjection.create(key, emptyList(), emptyList(), true, true)
        val result = empty.resolve(a)!!
        assertEquals(AnchorResolution.NoContent(a), result.logical)
        assertNull(result.target); assertNull(empty.capture(0))
    }

    @Test fun keylessProjectionKeepsRenderingButNeverInventsPortableIdentity() {
        val p = ReadingProjection.create(null, raw, listOf(ProjectedPage(2, raw[2])))
        assertEquals(raw, p.originalPages)
        assertNull(p.document); assertNull(p.capture(0))
        assertNull(p.resolve(projection(raw.indices.toList()).capture(2)!!))
    }

    @Test fun relocationAndChangedFallbackRetainCoreOutcomesAndRejectBadAnchors() {
        val old = projection(listOf(2), true)
        val a = old.capture(1)!!
        val nextRaw = listOf(NovelPage(0, PageContent.Text("new"))) + raw
        val next = ReadingProjection.create(key, nextRaw, listOf(ProjectedPage(3, nextRaw[3])), true, true)
        val result = next.resolve(a)!!
        assertTrue(result.logical is AnchorResolution.Relocated)
        assertEquals(1, (result.target as ProjectionTarget.Item).renderedIndex)
        val differentRaw = listOf(NovelPage(0, PageContent.Text("changed")))
        val different = ReadingProjection.create(key, differentRaw, listOf(ProjectedPage(0, differentRaw[0])), true, true)
        val changed = different.resolve(a)!!
        assertEquals(AnchorResolution.Unresolved(a, UnresolvedAnchorReason.CONTENT_CHANGED), changed.logical)
        assertEquals(ProjectionTarget.Item(0, AnchorPosition.Start, false), changed.target)
        for (bad in listOf(a.copy(key = key.copy(url = "/other")), a.copy(modelVersion = 99), a.copy(logicalRevision = "wrong"))) {
            assertNull(next.resolve(bad)!!.target)
        }
    }

    @Test fun viewportConfigurationChangesCoordinatesWithoutChangingSemanticPosition() {
        val a = projection(raw.indices.toList()).capture(2, RenderedTextPosition("The harbor bell rang.", 4))!!
        val p = projection(listOf(2, 5), true)
        val result = p.resolve(a)!!
        assertEquals(AnchorResolution.Exact(a), result.logical)
        assertEquals(ProjectionTarget.Item(1, a.position, false), result.target)
        assertEquals(ProjectionTarget.Item(3, AnchorPosition.End, false), p.resolve(a.copy(position = AnchorPosition.End))!!.target)
        val noDecorations = projection(listOf(2, 5))
        assertEquals(ProjectionTarget.Item(1, AnchorPosition.End, false), noDecorations.resolve(a.copy(position = AnchorPosition.End))!!.target)
    }

    private fun projection(ordinals: List<Int>, decorated: Boolean = false) =
        ReadingProjection.create(key, raw, ordinals.map { ProjectedPage(it, raw[it]) }, decorated, decorated)
}
