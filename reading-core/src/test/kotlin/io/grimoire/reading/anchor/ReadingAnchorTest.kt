package io.grimoire.reading.anchor

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import org.junit.Assert.*
import org.junit.Test

class ReadingAnchorTest {
    private val key = ChapterKey(NovelKey(17, "/novel"), "/chapter")

    @Test fun exactBoundariesAndBothTextPrecisionsRetainTheOriginalEvidence() {
        val doc = textDocument("same", "same")
        val anchors = listOf(anchor(doc, AnchorPosition.Start), anchor(doc, AnchorPosition.End),
            block(doc, 0, 0, AnchorPrecision.BLOCK_START), block(doc, 1, 0), block(doc, 1, 4))
        for (a in anchors) {
            assertEquals(AnchorResolution.Exact(a), resolveReadingAnchor(a, doc))
            assertSame(a, resolveReadingAnchor(a, doc).original)
        }
    }

    @Test fun textOffsetsUseExactUtf16UnitsWithoutSplittingValidSurrogatePairs() {
        val text = "A\uD83D\uDE00e\u0301\uD800x\uDC00"
        val doc = textDocument(text)
        for (offset in 0..text.length) {
            val a = block(doc, 0, offset)
            if (offset == 2) invalid(a, doc, InvalidAnchorReason.SPLIT_SURROGATE_PAIR)
            else assertEquals("offset $offset", AnchorResolution.Exact(a), resolveReadingAnchor(a, doc))
        }
        invalid(block(doc, 0, text.length + 1), doc, InvalidAnchorReason.OFFSET_OUT_OF_RANGE)
    }

    @Test fun imageSeparatorAndEmptyTextHaveExplicitPrecisionRules() {
        val doc = document(PageContent.Image("remote"), PageContent.Separator(), PageContent.Text(""))
        for (index in 0..1) {
            val start = block(doc, index, 0, AnchorPrecision.BLOCK_START)
            assertEquals(AnchorResolution.Exact(start), resolveReadingAnchor(start, doc))
            invalid(block(doc, index, 0), doc, InvalidAnchorReason.PRECISION)
            invalid(block(doc, index, 1, AnchorPrecision.BLOCK_START), doc, InvalidAnchorReason.PRECISION)
        }
        val empty = block(doc, 2, 0)
        assertEquals(AnchorResolution.Exact(empty), resolveReadingAnchor(empty, doc))
        invalid(block(doc, 2, 1), doc, InvalidAnchorReason.OFFSET_OUT_OF_RANGE)
    }

    @Test fun uniqueBlockRelocatesAfterInsertionWithoutPersistingOrChangingPrecision() {
        val old = textDocument("Before", "The harbor bell rang.", "After")
        val fresh = textDocument("New opening", "Before", "The harbor bell rang.", "After")
        for (precision in AnchorPrecision.entries) {
            val a = block(old, 1, if (precision == AnchorPrecision.TEXT_OFFSET) 7 else 0, precision)
            val position = a.position as AnchorPosition.Block
            val expected = a.copy(logicalRevision = fresh.logicalRevision, position = position.copy(ordinal = 2))
            val result = resolveReadingAnchor(a, fresh)
            assertEquals(AnchorResolution.Relocated(a, expected), result)
            assertSame(a, result.original)
            assertEquals(old.logicalRevision, a.logicalRevision)
            assertEquals(1, position.ordinal)
        }
    }

    @Test fun relocationDoesNotRequireTheStoredOrdinalToExistInTheShortenedDocument() {
        val old = textDocument(*((0 until 10).map { "old $it" } + "target").toTypedArray())
        val fresh = textDocument("target")
        val a = block(old, 10, 3)
        val expected = block(fresh, 0, 3)
        assertEquals(AnchorResolution.Relocated(a, expected), resolveReadingAnchor(a, fresh))
    }

    @Test fun changedTextWhitespaceSplitsAndMissingBlocksNeverReuseTheOldOffset() {
        val old = textDocument("Target text")
        val a = block(old, 0, 4)
        for (fresh in listOf(textDocument("target text"), textDocument("Target text "), textDocument("Target", " text"), textDocument("different"))) {
            unresolved(a, fresh, UnresolvedAnchorReason.CONTENT_CHANGED)
        }
    }

    @Test fun changedStartOrEndNeverSkipsNewlyInsertedOrAppendedText() {
        val old = textDocument("original")
        for (position in listOf(AnchorPosition.Start, AnchorPosition.End)) {
            val a = anchor(old, position)
            unresolved(a, textDocument("original", "appended"), UnresolvedAnchorReason.CONTENT_CHANGED)
            unresolved(a, textDocument("inserted", "original"), UnresolvedAnchorReason.CONTENT_CHANGED)
        }
    }

    @Test fun repeatedMatchesAreAmbiguousEvenWhenTheOldOrdinalStillMatches() {
        val a = block(textDocument("same"), 0, 2)
        for (fresh in listOf(textDocument("same", "same"), textDocument("prefix", "same", "same"))) {
            unresolved(a, fresh, UnresolvedAnchorReason.AMBIGUOUS_BLOCK)
        }
        val images = document(PageContent.Image("embedded"))
        unresolved(block(images, 0, 0, AnchorPrecision.BLOCK_START),
            document(PageContent.Image("embedded"), PageContent.Image("embedded")), UnresolvedAnchorReason.AMBIGUOUS_BLOCK)
    }

    @Test fun nonTextBlocksCanRelocateOnlyWithBlockStartPrecision() {
        val old = document(PageContent.Image("remote"), PageContent.Separator())
        val fresh = document(PageContent.Text("new"), PageContent.Image("remote"), PageContent.Separator())
        for (ordinal in 0..1) {
            val a = block(old, ordinal, 0, AnchorPrecision.BLOCK_START)
            assertEquals(AnchorResolution.Relocated(a, block(fresh, ordinal + 1, 0, AnchorPrecision.BLOCK_START)), resolveReadingAnchor(a, fresh))
            invalid(block(old, ordinal, 0), fresh, InvalidAnchorReason.PRECISION)
        }
    }

    @Test fun relocationValidatesOffsetAndSurrogateBoundaryAgainstTheLocatedBlock() {
        val old = textDocument("\uD83D\uDE00x")
        val fresh = textDocument("new", "\uD83D\uDE00x")
        invalid(block(old, 0, 4), fresh, InvalidAnchorReason.OFFSET_OUT_OF_RANGE)
        invalid(block(old, 0, 1), fresh, InvalidAnchorReason.SPLIT_SURROGATE_PAIR)
        invalid(block(old, 0, 1, AnchorPrecision.BLOCK_START), fresh, InvalidAnchorReason.PRECISION)
    }

    @Test fun wrongSourceNovelAndChapterKeysCannotResolveIdenticalContent() {
        val doc = textDocument("same")
        val a = block(doc, 0, 1)
        for (other in listOf(key.copy(novel = NovelKey(18, key.novel.url)),
            key.copy(novel = key.novel.copy(url = "/other")), key.copy(url = "/other"))) {
            val wrong = a.copy(key = other)
            assertEquals(AnchorResolution.IdentityMismatch(wrong, key), resolveReadingAnchor(wrong, doc))
            // Identity is checked even if content is empty or the envelope is unsupported.
            val unsupported = wrong.copy(modelVersion = 99)
            assertEquals(AnchorResolution.IdentityMismatch(unsupported, key), resolveReadingAnchor(unsupported, textDocument()))
        }
    }

    @Test fun unknownVersionsAreReportedWithoutRewritingOrInterpretingTheirFields() {
        val doc = textDocument("same")
        for (version in listOf(-1, 0, 2, Int.MAX_VALUE)) {
            val a = anchor(doc, AnchorPosition.Start).copy(modelVersion = version, logicalRevision = "unknown encoding")
            val result = resolveReadingAnchor(a, doc)
            assertEquals(AnchorResolution.UnsupportedVersion(a), result)
            assertSame(a, result.original)
        }
    }

    @Test fun malformedFingerprintsAndNumericShapeAreInvalidBeforeEmptyOrChangedResolution() {
        val doc = textDocument("same")
        val a = block(doc, 0, 1)
        val position = a.position as AnchorPosition.Block
        val cases = listOf(
            a.copy(logicalRevision = "") to InvalidAnchorReason.LOGICAL_REVISION,
            a.copy(logicalRevision = "F".repeat(64)) to InvalidAnchorReason.LOGICAL_REVISION,
            a.copy(logicalRevision = "a".repeat(63)) to InvalidAnchorReason.LOGICAL_REVISION,
            a.copy(logicalRevision = "a".repeat(64) + "\n") to InvalidAnchorReason.LOGICAL_REVISION,
            a.copy(position = position.copy(fingerprint = "wrong")) to InvalidAnchorReason.BLOCK_FINGERPRINT,
            a.copy(position = position.copy(fingerprint = "F".repeat(64))) to InvalidAnchorReason.BLOCK_FINGERPRINT,
            a.copy(position = position.copy(ordinal = -1)) to InvalidAnchorReason.NEGATIVE_ORDINAL,
            a.copy(position = position.copy(textOffset = -1)) to InvalidAnchorReason.NEGATIVE_OFFSET,
            a.copy(position = position.copy(precision = AnchorPrecision.BLOCK_START)) to InvalidAnchorReason.PRECISION,
        )
        for ((bad, reason) in cases) for (target in listOf(doc, textDocument(), textDocument("changed"))) invalid(bad, target, reason)
    }

    @Test fun sameRevisionNeverClampsAnOrdinalOrRepairsMismatchedBlockEvidence() {
        val doc = textDocument("first", "second")
        val a = block(doc, 0, 1)
        val position = a.position as AnchorPosition.Block
        for (ordinal in listOf(2, Int.MAX_VALUE)) invalid(a.copy(position = position.copy(ordinal = ordinal)), doc, InvalidAnchorReason.ORDINAL_OUT_OF_RANGE)
        invalid(a.copy(position = position.copy(ordinal = 1)), doc, InvalidAnchorReason.BLOCK_MISMATCH)
        invalid(a.copy(position = position.copy(fingerprint = "0".repeat(64))), doc, InvalidAnchorReason.BLOCK_MISMATCH)
        invalid(a.copy(position = position.copy(textOffset = Int.MAX_VALUE)), doc, InvalidAnchorReason.OFFSET_OUT_OF_RANGE)
    }

    @Test fun emptyDocumentPreservesValidOldEvidenceWithoutInventingAPositionOrCompletion() {
        val old = textDocument("original")
        for (a in listOf(anchor(old, AnchorPosition.Start), anchor(old, AnchorPosition.End), block(old, 0, 2))) {
            val result = resolveReadingAnchor(a, textDocument())
            assertEquals(AnchorResolution.NoContent(a), result)
            assertSame(a, result.original)
        }
        val empty = textDocument()
        val start = anchor(empty, AnchorPosition.Start)
        assertEquals(AnchorResolution.NoContent(start), resolveReadingAnchor(start, empty))
    }

    @Test fun backwardPositionsAreResolvedIndependentlyWithoutAFurthestReadRule() {
        val doc = textDocument("abcdef")
        for (offset in listOf(6, 4, 1, 0)) {
            val a = block(doc, 0, offset)
            assertEquals(AnchorResolution.Exact(a), resolveReadingAnchor(a, doc))
        }
    }

    private fun textDocument(vararg text: String) = document(*text.map { PageContent.Text(it) }.toTypedArray())
    private fun document(vararg pages: PageContent) = CanonicalDocument.fromPages(key, pages.mapIndexed { i, content -> NovelPage(i, content) })
    private fun anchor(doc: CanonicalDocument, position: AnchorPosition) = ReadingAnchor(doc.key, doc.logicalRevision, position)
    private fun block(doc: CanonicalDocument, index: Int, offset: Int, precision: AnchorPrecision = AnchorPrecision.TEXT_OFFSET) =
        anchor(doc, AnchorPosition.Block(index, doc.blockFingerprint(index), offset, precision))

    private fun invalid(a: ReadingAnchor, doc: CanonicalDocument, reason: InvalidAnchorReason) {
        val result = resolveReadingAnchor(a, doc)
        assertEquals(AnchorResolution.Invalid(a, reason), result)
        assertSame(a, result.original)
    }

    private fun unresolved(a: ReadingAnchor, doc: CanonicalDocument, reason: UnresolvedAnchorReason) {
        val result = resolveReadingAnchor(a, doc)
        assertEquals(AnchorResolution.Unresolved(a, reason), result)
        assertSame(a, result.original)
        assertEquals(AnchorPosition.Start, (result as AnchorResolution.Unresolved).fallback)
    }
}
