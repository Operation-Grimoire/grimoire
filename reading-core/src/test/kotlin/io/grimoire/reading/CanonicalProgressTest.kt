package io.grimoire.reading

import io.grimoire.api.model.novel.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.state.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalProgressTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val mixed = listOf(PageContent.Text("Alpha"), PageContent.Separator(), PageContent.Text(" \t"),
        PageContent.Image("https://images.invalid/a"), PageContent.Text("Beta"))
    private fun document(blocks: List<PageContent> = mixed) = CanonicalDocument.fromPages(key, blocks.mapIndexed { i, p -> NovelPage(i, p) })
    private fun anchor(doc: CanonicalDocument, i: Int, offset: Int = 0, precision: AnchorPrecision = AnchorPrecision.BLOCK_START) =
        ReadingAnchor(key, doc.logicalRevision, AnchorPosition.Block(i, doc.blockFingerprint(i), offset, precision))
    private fun check(doc: CanonicalDocument, anchor: ReadingAnchor, numerator: Long, denominator: Long,
        precision: ProgressPrecision = ProgressPrecision.BLOCK_START): CanonicalProgress {
        val measure = requireNotNull(measureReadingPosition(anchor, doc).measure)
        assertEquals(numerator, measure.numerator); assertEquals(denominator, measure.denominator)
        assertEquals(precision, measure.precision); assertEquals(key, measure.key)
        assertEquals(doc.logicalRevision, measure.logicalRevision); assertEquals(1, measure.modelVersion)
        assertTrue(measure.fraction.isFinite() && measure.fraction in 0.0..1.0)
        return measure
    }

    @Test fun mixedFrozenVectorsRetainTypedOriginalOrdinalsAndExactBasis() {
        val doc = document()
        assertEquals(3, check(doc, anchor(doc, 0), 0, 3).unitCount)
        check(doc, anchor(doc, 1), 1, 3, ProgressPrecision.BOUNDARY)
        check(doc, anchor(doc, 2), 1, 3, ProgressPrecision.BOUNDARY)
        check(doc, anchor(doc, 3), 1, 3)
        check(doc, anchor(doc, 4), 2, 3)
        check(doc, anchor(doc, 4, 2, AnchorPrecision.TEXT_OFFSET), 10, 12, ProgressPrecision.TEXT_OFFSET)
        assertEquals(mixed.size, doc.blocks.size)
    }

    @Test fun startEndAndTrailingZeroUnitBoundaryAreMeasuresNotCompletion() {
        val doc = document(listOf(PageContent.Text("Text"), PageContent.Separator(), PageContent.Text(" ")))
        check(doc, ReadingAnchor(key, doc.logicalRevision, AnchorPosition.Start), 0, 1, ProgressPrecision.BOUNDARY)
        check(doc, anchor(doc, 0), 0, 1)
        check(doc, anchor(doc, 1), 1, 1, ProgressPrecision.BOUNDARY)
        check(doc, anchor(doc, 2, 1, AnchorPrecision.TEXT_OFFSET), 1, 1, ProgressPrecision.BOUNDARY)
        check(doc, ReadingAnchor(key, doc.logicalRevision, AnchorPosition.End), 1, 1, ProgressPrecision.BOUNDARY)
    }

    @Test fun emptyBlankAndSeparatorOnlyDocumentsHaveNoMeasureEvenAtEnd() {
        for (blocks in listOf(emptyList(), listOf(PageContent.Text(" \t\n"), PageContent.Separator()))) {
            val doc = document(blocks)
            for (p in listOf(AnchorPosition.Start, AnchorPosition.End)) {
                assertNull(measureReadingPosition(ReadingAnchor(key, doc.logicalRevision, p), doc).measure)
            }
        }
    }

    @Test fun textWeightsAreBlockBasedAndDoNotReparseHtmlOrSourceIndices() {
        val doc = document(listOf(PageContent.Text("x"), PageContent.Text("longer text"), PageContent.Text("<p> </p>")))
        assertEquals(3, check(doc, anchor(doc, 1), 1, 3).unitCount)
        check(doc, anchor(doc, 2), 2, 3)
    }

    @Test fun utf16FrozenOffsetsRetainCombiningAndUnpairedUnitsButRejectSplitPairs() {
        val doc = document(listOf(PageContent.Text("A\uD83D\uDE00e\u0301\uD800")))
        for (offset in listOf(0, 1, 3, 4, 5, 6)) check(doc, anchor(doc, 0, offset, AnchorPrecision.TEXT_OFFSET),
            offset.toLong(), 6, ProgressPrecision.TEXT_OFFSET)
        val split = measureReadingPosition(anchor(doc, 0, 2, AnchorPrecision.TEXT_OFFSET), doc)
        assertNull(split.measure); assertEquals(InvalidAnchorReason.SPLIT_SURROGATE_PAIR, (split.resolution as AnchorResolution.Invalid).reason)
    }

    @Test fun malformedUnsupportedAndDifferentIdentityRetainTheirResolutionWithoutZeroFallback() {
        val doc = document(); val valid = anchor(doc, 4)
        val invalid = listOf(valid.copy(logicalRevision = "bad"), valid.copy(position = (valid.position as AnchorPosition.Block).copy(textOffset = -1)),
            anchor(doc, 4, 5, AnchorPrecision.TEXT_OFFSET), anchor(doc, 4, 1))
        for (a in invalid) {
            val result = measureReadingPosition(a, doc); assertNull(result.measure); assertTrue(result.resolution is AnchorResolution.Invalid)
        }
        val unsupported = measureReadingPosition(valid.copy(modelVersion = 99), doc)
        assertNull(unsupported.measure); assertTrue(unsupported.resolution is AnchorResolution.UnsupportedVersion)
        val other = measureReadingPosition(valid.copy(key = key.copy(url = "/other")), doc)
        assertNull(other.measure); assertTrue(other.resolution is AnchorResolution.IdentityMismatch)
        val imageOffset = measureReadingPosition(anchor(doc, 3, 0, AnchorPrecision.TEXT_OFFSET), doc)
        assertNull(imageOffset.measure); assertTrue(imageOffset.resolution is AnchorResolution.Invalid)
    }

    @Test fun uniqueRelocationLabelsTheNewRevisionAndPreservesOriginalEvidence() {
        val original = document(); val saved = anchor(original, 4, 2, AnchorPrecision.TEXT_OFFSET)
        val changed = document(listOf(PageContent.Text("Intro")) + mixed)
        val result = measureReadingPosition(saved, changed)
        assertTrue(result.resolution is AnchorResolution.Relocated)
        assertEquals(original.logicalRevision, result.resolution.original.logicalRevision)
        assertEquals(14L, result.measure!!.numerator); assertEquals(16L, result.measure!!.denominator)
        assertEquals(changed.logicalRevision, result.measure!!.logicalRevision)
        assertEquals(4, (saved.position as AnchorPosition.Block).ordinal)
    }

    @Test fun changedOrAmbiguousContentHasNoMeasureEvenThoughResolverOffersStartFallback() {
        val doc = document(); val saved = anchor(doc, 4)
        for (replacement in listOf(listOf(PageContent.Text("Replacement")), mixed + PageContent.Text("Beta"))) {
            val result = measureReadingPosition(saved, document(replacement))
            assertTrue(result.resolution is AnchorResolution.Unresolved); assertNull(result.measure)
        }
        assertNull(measureReadingPosition(ReadingAnchor(key, doc.logicalRevision, AnchorPosition.End), document(mixed.reversed())).measure)
    }

    @Test fun renderedFilteringAndCachedImageLocatorsDoNotChangeOriginalMeasure() {
        val pages = mixed.mapIndexed { i, c -> NovelPage(900 - i, c) }
        val hidden = ReadingProjection.create(key, pages, emptyList(), true, true)
        val cached = ReadingProjection.create(key, pages, listOf(ProjectedPage(3, NovelPage(4, PageContent.Image("file:///cached")))))
        val saved = anchor(document(), 3)
        check(hidden.document!!, saved, 1, 3); check(cached.document!!, saved, 1, 3)
        assertNull(hidden.capture(0)) // A mathematical measure grants no visual authority.
    }

    @Test fun backwardReadingReplacesCurrentMeasureWithoutAFurthestWatermark() {
        val doc = document()
        val later = check(doc, anchor(doc, 4), 2, 3)
        val earlier = check(doc, anchor(doc, 0), 0, 3)
        assertTrue(earlier.fraction < later.fraction)
    }

    @Test fun maximumIntSizedBasisCannotOverflowLongOrInventInfinity() {
        val ratio = canonicalPositionRatio(Int.MAX_VALUE - 1, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(4611686014132420609L to 4611686014132420609L, ratio)
        assertEquals(0L to 2147483647L, canonicalPositionRatio(0, Int.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) { canonicalPositionRatio(1, 1, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { canonicalPositionRatio(0, 0) }
    }
}
