package io.grimoire.reading

import io.grimoire.api.model.novel.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.state.*
import io.grimoire.reading.storage.*
import org.junit.Assert.*
import org.junit.Test

class ReadingCompletionTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val pages = listOf(NovelPage(0, PageContent.Text("Text")))
    private val projection = ReadingProjection.create(key, pages, listOf(ProjectedPage(0, pages[0])), true, true)
    private val doc = projection.document!!
    private val observation = ChapterStorageObservation(key, "scope", "generation", ChapterContentPresence.SAVED)
    private val state = ReadingCompletionState(key, ReadStatus.UNREAD, null)
    private fun viewport(last: Int, total: Int = 10, first: Int = 0) = requireNotNull(AndroidViewportV1.create(first, last, total, (last + 1).toFloat() / total))
    private fun visual() = ReadingCompletionInput.Visual(key,
        VisualCompletionEvidence("load", observation, doc.logicalRevision, viewport(2, 3)),
        VisualCompletionContext("load", observation, projection, true, true), AutomaticReadPolicy.AtEnd, 100)
    private fun audio() = ReadingCompletionInput.Audio(key,
        AudioCompletionEvidence("playback", observation, doc.logicalRevision, 2, 1, true),
        AudioCompletionContext("playback", observation, doc, 2, true, true), 100)
    private fun proposed(s: ReadingCompletionState, input: ReadingCompletionInput) =
        (decideReadingCompletion(s, input) as ReadingCompletionDecision.Proposed).state
    private fun rejected(input: ReadingCompletionInput, reason: CompletionRejection = CompletionRejection.INELIGIBLE) =
        assertEquals(ReadingCompletionDecision.Rejected(reason), decideReadingCompletion(state, input))

    @Test fun androidThresholdFrozenVectorsRetainInclusiveDecorationBasedBoundaries() {
        assertFalse(shouldCompleteReading(viewport(93, 100), AutomaticReadPolicy.Percent(95)))
        assertTrue(shouldCompleteReading(viewport(94, 100), AutomaticReadPolicy.Percent(95)))
        assertFalse(shouldCompleteReading(viewport(5), AutomaticReadPolicy.ItemsFromEnd(3)))
        assertTrue(shouldCompleteReading(viewport(6), AutomaticReadPolicy.ItemsFromEnd(3)))
        assertFalse(shouldCompleteReading(viewport(8), AutomaticReadPolicy.AtEnd))
        assertTrue(shouldCompleteReading(viewport(9), AutomaticReadPolicy.AtEnd))
        assertFalse(shouldCompleteReading(viewport(9), AutomaticReadPolicy.Disabled))
        assertThrows(IllegalArgumentException::class.java) { AutomaticReadPolicy.Percent(49) }
        assertThrows(IllegalArgumentException::class.java) { AutomaticReadPolicy.ItemsFromEnd(21) }
    }

    @Test fun malformedAndInconsistentViewportSamplesCannotBeConstructed() {
        for (f in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.1f, 1.1f, 0.5f))
            assertNull(AndroidViewportV1.create(0, 9, 10, f))
        assertNull(AndroidViewportV1.create(-1, 9, 10, 1f)); assertNull(AndroidViewportV1.create(3, 2, 10, 0.3f))
        assertNull(AndroidViewportV1.create(0, 10, 10, 1f)); assertNull(AndroidViewportV1.create(0, 0, 0, 1f))
        assertNotNull(AndroidViewportV1.create(0, Int.MAX_VALUE - 1, Int.MAX_VALUE, 1f))
    }

    @Test fun explicitReadAndUnreadPreserveFirstReadAndNeverMovePositionOrHistory() {
        val read = proposed(state, ReadingCompletionInput.MarkRead(key, 100))
        assertEquals(ReadingCompletionState(key, ReadStatus.READ, 100), read)
        val unread = proposed(read, ReadingCompletionInput.MarkUnread(key))
        assertEquals(ReadStatus.UNREAD, unread.status); assertEquals(100L, unread.firstReadAt)
        assertEquals(read, proposed(unread, ReadingCompletionInput.MarkRead(key, 999)))
        assertEquals(read, proposed(read, ReadingCompletionInput.MarkRead(key, 999)))
        assertTrue(decideReadingCompletion(unread, ReadingCompletionInput.MarkUnread(key)) is ReadingCompletionDecision.Proposed)
    }

    @Test fun unknownDesktopStateIsNotInventedByDisabledPolicyOrPositionMeasures() {
        val unknown = ReadingCompletionState(key, ReadStatus.UNKNOWN, null)
        assertEquals(ReadingCompletionDecision.Unchanged, decideReadingCompletion(unknown, visual().copy(policy = AutomaticReadPolicy.Disabled)))
        measureReadingPosition(ReadingAnchor(key, doc.logicalRevision, AnchorPosition.End), doc)
        assertEquals(ReadingCompletionState(key, ReadStatus.UNKNOWN, null), unknown)
        assertEquals(ReadStatus.UNREAD, proposed(unknown, ReadingCompletionInput.MarkUnread(key)).status)
    }

    @Test fun laterExplicitOrAudioReadCanFillMissingTimeOnAlreadyReadRows() {
        val missing = state.copy(status = ReadStatus.READ)
        assertEquals(100L, proposed(missing, ReadingCompletionInput.MarkRead(key, 100)).firstReadAt)
        assertEquals(100L, proposed(missing, audio()).firstReadAt)
        assertEquals(ReadingCompletionDecision.Unchanged, decideReadingCompletion(missing, visual()))
        assertEquals(-17L, proposed(missing.copy(firstReadAt = -17), ReadingCompletionInput.MarkRead(key, 100)).firstReadAt)
        rejected(ReadingCompletionInput.MarkRead(key, -1), CompletionRejection.INVALID)
    }

    @Test fun nextRetainsItsLoadedChapterSuccessorAndAlreadyReadConditions() {
        val next = ReadingCompletionInput.Next(key, key, true, 100)
        assertEquals(ReadStatus.READ, proposed(state, next).status)
        for (input in listOf(next.copy(hasSuccessor = false), next.copy(loadedChapter = null), next.copy(loadedChapter = key.copy(url = "other"))))
            assertEquals(ReadingCompletionDecision.Unchanged, decideReadingCompletion(state, input))
        assertEquals(ReadingCompletionDecision.Unchanged, decideReadingCompletion(state.copy(status = ReadStatus.READ), next))
    }

    @Test fun visualRequiresFreshLoadContentBodyAndActualInputIndependentlyOfThreshold() {
        val good = visual(); assertEquals(ReadStatus.READ, proposed(state, good).status)
        val wrong = listOf(good.copy(current = good.current.copy(loadId = "new")), good.copy(current = good.current.copy(loadId = "")),
            good.copy(current = good.current.copy(userInputEnabled = false)), good.copy(current = good.current.copy(observationCurrent = false)),
            good.copy(evidence = good.evidence.copy(observation = observation.copy(generation = "old"))),
            good.copy(evidence = good.evidence.copy(logicalRevision = "old")), good.copy(evidence = good.evidence.copy(viewport = viewport(9))))
        wrong.forEach { rejected(it) }
        val hidden = ReadingProjection.create(key, pages, emptyList(), true, true)
        rejected(good.copy(evidence = good.evidence.copy(viewport = viewport(1, 2)), current = good.current.copy(projection = hidden)))
        val blank = listOf(NovelPage(0, PageContent.Text(" ")), NovelPage(1, PageContent.Separator()))
        val p = ReadingProjection.create(key, blank, blank.mapIndexed { i, page -> ProjectedPage(i, page) })
        rejected(good.copy(evidence = good.evidence.copy(viewport = viewport(1, 2), logicalRevision = p.document!!.logicalRevision),
            current = good.current.copy(projection = p)))
    }

    @Test fun backgroundAudioNeedsNoVisualReaderAndPreservesExistingReadTime() {
        assertEquals(ReadingCompletionState(key, ReadStatus.READ, 100), proposed(state, audio()))
        assertEquals(7L, proposed(state.copy(firstReadAt = 7), audio()).firstReadAt)
    }

    @Test fun stalePausedReplacedOrIncompleteAudioCannotProposeCompletion() {
        val good = audio()
        listOf(good.copy(current = good.current.copy(playing = false)), good.copy(current = good.current.copy(observationCurrent = false)),
            good.copy(current = good.current.copy(playbackId = "new")), good.copy(current = good.current.copy(playbackId = "")),
            good.copy(evidence = good.evidence.copy(observation = observation.copy(scope = "other"))),
            good.copy(evidence = good.evidence.copy(logicalRevision = "wrong")), good.copy(evidence = good.evidence.copy(engineCompleted = false)),
            good.copy(evidence = good.evidence.copy(completedUtterance = 0)), good.copy(current = good.current.copy(utteranceCount = 0)),
            good.copy(evidence = good.evidence.copy(utteranceCount = 3))).forEach { rejected(it) }
        val image = CanonicalDocument.fromPages(key, listOf(NovelPage(0, PageContent.Image("image"))))
        rejected(good.copy(current = good.current.copy(document = image), evidence = good.evidence.copy(logicalRevision = image.logicalRevision)))
    }

    @Test fun allInputsRejectAnotherChapterWithoutChangingTheOriginalState() {
        val other = key.copy(url = "/other")
        listOf(ReadingCompletionInput.MarkRead(other, 100), ReadingCompletionInput.MarkUnread(other),
            ReadingCompletionInput.Next(other, other, true, 100), visual().copy(key = other), audio().copy(key = other))
            .forEach { rejected(it, CompletionRejection.IDENTITY) }
        rejected(visual().copy(evidence = visual().evidence.copy(observation = observation.copy(key = other))), CompletionRejection.IDENTITY)
        rejected(audio().copy(current = audio().current.copy(observation = observation.copy(key = other))), CompletionRejection.IDENTITY)
        assertEquals(ReadingCompletionState(key, ReadStatus.UNREAD, null), state)
    }

    @Test fun freshInputCanCompleteRereadingWhileBelowThresholdPreservesState() {
        val old = proposed(state, ReadingCompletionInput.MarkRead(key, 7))
        val rereading = proposed(old, ReadingCompletionInput.MarkUnread(key))
        assertEquals(ReadingCompletionDecision.Unchanged, decideReadingCompletion(rereading, visual().copy(policy = AutomaticReadPolicy.Disabled)))
        assertEquals(old, proposed(rereading, visual()))
    }
}
