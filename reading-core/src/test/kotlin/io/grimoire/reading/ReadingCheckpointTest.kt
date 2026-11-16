package io.grimoire.reading

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.*
import org.junit.Assert.*
import org.junit.Test

class ReadingCheckpointTest {
    private val key = ChapterKey(NovelKey(17, "/novel"), "/chapter")
    private val document = CanonicalDocument.fromPages(key, listOf(NovelPage(0, PageContent.Text("A\uD83D\uDE00B"))))
    private val observation = ChapterStorageObservation(key, "open", "generation", ChapterContentPresence.ABSENT)
    private val session = ReadingSession("session", observation, document, null)
    private fun anchor(position: AnchorPosition = AnchorPosition.Start) = ReadingAnchor(key, document.logicalRevision, position)
    private fun command(sequence: Long, position: AnchorPosition = AnchorPosition.Start) = CheckpointCommand(session, sequence, anchor(position))
    private fun rejected(reason: CheckpointRejection, command: CheckpointCommand, state: ReadingCheckpointState) {
        assertEquals(CheckpointCommit.NotApplied(reason), decideCheckpoint(command, state, true))
    }

    @Test fun orderingUsesSequenceAndAllowsBackwardPositionsWithExactRetryAndConflicts() {
        val state = ReadingCheckpointState(session.binding, 9, anchor(AnchorPosition.End))
        assertEquals(CheckpointCommit.Committed(anchor(), 10, false), decideCheckpoint(command(10), state, true))
        assertEquals(CheckpointCommit.Committed(state.anchor!!, 9, true), decideCheckpoint(command(9, AnchorPosition.End), state, true))
        rejected(CheckpointRejection.CONFLICT, command(9), state)
        rejected(CheckpointRejection.STALE, command(8), state)
        rejected(CheckpointRejection.INVALID, command(0), state)
        rejected(CheckpointRejection.INVALID, command(-1), state)
        assertEquals(CheckpointCommit.Committed(anchor(), Long.MAX_VALUE, false), decideCheckpoint(command(Long.MAX_VALUE), state, true))
    }

    @Test fun everyBindingComponentAndCurrentObservationIsRequiredEvenForReplay() {
        val state = ReadingCheckpointState(session.binding, 1, anchor())
        for (different in listOf(session.copy(id = "new"),
            session.copy(observation = observation.copy(scope = "reopen")),
            session.copy(observation = observation.copy(generation = "new")),
            session.copy(observation = observation.copy(presence = ChapterContentPresence.SAVED)),
            session.copy(document = CanonicalDocument.fromPages(key, listOf(NovelPage(0, PageContent.Text("Changed"))))))) {
            rejected(CheckpointRejection.STALE, command(1).copy(session = different), state)
        }
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), decideCheckpoint(command(1), state, false))
        assertEquals(CheckpointCommit.NotApplied(CheckpointRejection.STALE), decideCheckpoint(command(1), null, true))
    }

    @Test fun wrongIdentityRevisionVersionAndInvalidOffsetsCannotBecomeCheckpoints() {
        val state = ReadingCheckpointState(session.binding)
        val block = AnchorPosition.Block(0, document.blockFingerprint(0), 0, AnchorPrecision.TEXT_OFFSET)
        for (bad in listOf(anchor().copy(key = key.copy(url = "/other")), anchor().copy(logicalRevision = "0".repeat(64)),
            anchor().copy(modelVersion = 2), anchor(block.copy(textOffset = 2)), anchor(block.copy(textOffset = 9)),
            anchor(block.copy(ordinal = 1)), anchor(block.copy(fingerprint = "0".repeat(64))),
            anchor(block.copy(textOffset = 1, precision = AnchorPrecision.BLOCK_START)))) {
            rejected(CheckpointRejection.INVALID, command(1).copy(anchor = bad), state)
        }
        assertTrue(decideCheckpoint(command(1).copy(anchor = anchor(block.copy(textOffset = 3))), state, true) is CheckpointCommit.Committed)
    }

    @Test fun emptyOrMismatchedDocumentCannotRegisterOrGainAuthorityFromAToken() {
        val empty = CanonicalDocument.fromPages(key, emptyList())
        val other = CanonicalDocument.fromPages(key.copy(url = "/other"), listOf(NovelPage(0, PageContent.Text("A\uD83D\uDE00B"))))
        for (doc in listOf(empty, other)) {
            assertFalse(canRegisterReadingSession(observation, doc))
            val forged = session.copy(document = doc)
            rejected(CheckpointRejection.INVALID, CheckpointCommand(forged, 1, ReadingAnchor(doc.key, doc.logicalRevision, AnchorPosition.Start)),
                ReadingCheckpointState(forged.binding))
        }
    }

    @Test fun versionedStateRoundTripsEveryPositionAndRawUnsupportedAnchorEvidence() {
        for (position in listOf(AnchorPosition.Start, AnchorPosition.End,
            AnchorPosition.Block(0, document.blockFingerprint(0), 3, AnchorPrecision.TEXT_OFFSET),
            AnchorPosition.Block(0, document.blockFingerprint(0), 0, AnchorPrecision.BLOCK_START))) {
            val state = ReadingCheckpointState(session.binding, 7, anchor(position))
            assertEquals(state, decodeReadingCheckpoint(encodeReadingCheckpoint(state)))
        }
        // Future/invalid anchor evidence is preserved on read; it is never accepted by commit.
        val raw = ReadingCheckpointState(session.binding, 7, anchor().copy(modelVersion = 99, logicalRevision = "raw"))
        assertEquals(raw, decodeReadingCheckpoint(encodeReadingCheckpoint(raw)))
        val state = ReadingCheckpointState(session.binding)
        assertEquals(state, decodeReadingCheckpoint(encodeReadingCheckpoint(state)))
        assertThrows(IllegalArgumentException::class.java) { decodeReadingCheckpoint(encodeReadingCheckpoint(state).replace("\"format\":1", "\"format\":99")) }
        assertThrows(NoSuchElementException::class.java) { decodeReadingCheckpoint("{}") }
        assertThrows(IllegalArgumentException::class.java) { decodeReadingCheckpoint(encodeReadingCheckpoint(state.copy(binding = session.binding.copy(id = "")))) }
        assertThrows(IllegalArgumentException::class.java) { decodeReadingCheckpoint(encodeReadingCheckpoint(state.copy(sequence = 1))) }
        for ((from, to) in listOf("\"session\":\"session\"" to "\"session\":123", "\"novel\":\"/novel\"" to "\"novel\":true",
            "\"format\":1" to "\"format\":\"1\"", "\"source\":17" to "\"source\":\"17\"", "\"sequence\":0" to "\"sequence\":\"0\"")) {
            val encoded = encodeReadingCheckpoint(state)
            assertTrue(encoded.contains(from))
            assertThrows(IllegalArgumentException::class.java) { decodeReadingCheckpoint(encoded.replace(from, to)) }
        }
        assertThrows(IllegalArgumentException::class.java) { encodeReadingCheckpoint(raw.copy(anchor = anchor().copy(key = key.copy(url = "/wrong")))) }
    }
}
