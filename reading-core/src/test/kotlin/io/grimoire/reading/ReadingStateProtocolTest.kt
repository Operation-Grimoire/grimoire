package io.grimoire.reading

import io.grimoire.api.model.novel.*
import io.grimoire.reading.anchor.*
import io.grimoire.reading.state.*
import io.grimoire.reading.storage.*
import org.junit.Assert.*
import org.junit.Test

class ReadingStateProtocolTest {
    private val key = ChapterKey(NovelKey(17, "/book"), "/chapter")
    private val value = ReadingCompletionState(key, ReadStatus.UNREAD, null)
    private val record = ReadingStateRecord("incarnation", 0, value)
    private val binding = ReadingStateBinding(key, "owner", record.incarnation, 0)
    private val content = ChapterStorageObservation(key, "content-owner", "generation", ChapterContentPresence.ABSENT)
    private val pages = listOf(NovelPage(0, PageContent.Text("Body")))
    private val projection = ReadingProjection.create(key, pages, listOf(ProjectedPage(0, pages[0])))
    private val document = projection.document!!
    private fun explicit(read: Boolean, id: String = "explicit", b: ReadingStateBinding = binding) = ReadingStateCommand(id, b,
        if (read) ReadingCompletionInput.MarkRead(key, 100) else ReadingCompletionInput.MarkUnread(key), null, 0)
    private fun session(kind: ReadingStateWriterKind = ReadingStateWriterKind.VISUAL, b: ReadingStateBinding = binding, id: String = "session") =
        ReadingStateSession(id, b, content, document, kind)
    private fun automatic(s: ReadingStateSession, seq: Long = 1, id: String = "auto"): ReadingStateCommand {
        val input = when (s.kind) {
            ReadingStateWriterKind.VISUAL -> ReadingCompletionInput.Visual(key,
                VisualCompletionEvidence(s.id, content, document.logicalRevision, AndroidViewportV1.create(0, 0, 1, 1f)!!),
                VisualCompletionContext(s.id, content, projection, true, true), AutomaticReadPolicy.AtEnd, 100)
            ReadingStateWriterKind.AUDIO -> ReadingCompletionInput.Audio(key,
                AudioCompletionEvidence(s.id, content, document.logicalRevision, 1, 0, true),
                AudioCompletionContext(s.id, content, document, 1, true, true), 100)
        }
        return ReadingStateCommand(id, s.binding, input, s, seq)
    }
    private fun decide(c: ReadingStateCommand, r: ReadingStateRecord? = record, receipt: ReadingStateReceipt? = null,
        scope: Boolean = true, current: Boolean = true) = decideReadingState(c, r, receipt, scope, current)
    private fun rejection(t: ReadingStateTransition, reason: ReadingStateRejection) {
        assertEquals(ReadingStateCommit.NotApplied(reason), t.result); assertNull(t.record); assertNull(t.receipt)
    }

    @Test fun sameValueExplicitIntentsAdvanceRevisionAndRetainTimestamp() {
        val first = decide(explicit(true)); assertEquals(1L, first.record!!.intentRevision)
        val second = decide(explicit(true, "second", binding.copy(intentRevision = 1)), first.record)
        assertEquals(2L, second.record!!.intentRevision); assertEquals(100L, second.record!!.value.firstReadAt)
        assertEquals(first.record!!.value, second.record!!.value)
        val unread = decide(explicit(false, "unread", binding.copy(intentRevision = 2)), second.record)
        assertEquals(ReadStatus.UNREAD, unread.record!!.value.status); assertEquals(100L, unread.record!!.value.firstReadAt)
        rejection(decide(explicit(false, "loser"), first.record), ReadingStateRejection.CONFLICT)
    }

    @Test fun replayConfirmsOldResultButNeverReplacesCurrentUnreadOrAdvancesAgain() {
        val command = explicit(true); val first = decide(command)
        val unread = decide(explicit(false, "unread", binding.copy(intentRevision = 1)), first.record)
        val replay = decide(command, unread.record, first.receipt, scope = false, current = false)
        val result = replay.result as ReadingStateCommit.Committed
        assertTrue(result.replayed); assertEquals(ReadStatus.READ, result.valueAtCommit.status)
        assertEquals(ReadStatus.UNREAD, result.current.status); assertEquals(2L, result.intentRevision)
        assertNull(replay.record); assertNull(replay.receipt)
        rejection(decide(command.copy(input = ReadingCompletionInput.MarkRead(key, 999)), unread.record, first.receipt), ReadingStateRejection.CONFLICT)
    }

    @Test fun everyAutomaticKindMustRebindAfterUnreadAndOldUnknownCommandsCannotApply() {
        for (kind in ReadingStateWriterKind.entries) {
            val s = session(kind); val active = bindReadingStateWriter(record, s); val firstCommand = automatic(s)
            val first = decide(firstCommand, active)
            val unread = decide(explicit(false, "unread"), first.record)
            rejection(decide(automatic(s, 2, "late"), unread.record), ReadingStateRejection.STALE)
            val fresh = session(kind, binding.copy(intentRevision = 1), "fresh")
            val rebound = bindReadingStateWriter(unread.record!!, fresh)
            val reread = decide(automatic(fresh, id = "fresh-command"), rebound)
            assertEquals(ReadStatus.READ, reread.record!!.value.status); assertEquals(100L, reread.record!!.value.firstReadAt)
            val replay = decide(firstCommand, unread.record, first.receipt).result as ReadingStateCommit.Committed
            assertTrue(replay.replayed); assertEquals(ReadStatus.UNREAD, replay.current.status)
        }
    }

    @Test fun identityIncarnationScopeAndReceiptIdentityRejectWithoutMutation() {
        val c = explicit(true); val first = decide(c)
        rejection(decide(c, null, first.receipt), ReadingStateRejection.STALE)
        rejection(decide(c, record.copy(incarnation = "recreated"), first.receipt), ReadingStateRejection.STALE)
        rejection(decide(c, scope = false), ReadingStateRejection.STALE)
        rejection(decide(c.copy(id = "")), ReadingStateRejection.INVALID)
        rejection(decide(c.copy(input = ReadingCompletionInput.MarkRead(key.copy(url = "other"), 100))), ReadingStateRejection.INVALID)
        rejection(decide(c, first.record, first.receipt!!.copy(commandId = "wrong")), ReadingStateRejection.INVALID)
        rejection(decide(c, record.copy(intentRevision = Long.MAX_VALUE), scope = true), ReadingStateRejection.CONFLICT)
        rejection(decide(c.copy(binding = binding.copy(intentRevision = Long.MAX_VALUE)), record.copy(intentRevision = Long.MAX_VALUE)), ReadingStateRejection.INVALID)
    }

    @Test fun activeSessionContentSequenceAndEvidenceMustAllAgree() {
        for (kind in ReadingStateWriterKind.entries) {
            val s = session(kind); val active = bindReadingStateWriter(record, s); val c = automatic(s)
            rejection(decide(c, active, current = false), ReadingStateRejection.STALE)
            rejection(decide(c.copy(session = s.copy(id = "forged")), active), ReadingStateRejection.STALE)
            rejection(decide(c.copy(sequence = 0), active), ReadingStateRejection.INVALID)
            rejection(decide(c.copy(session = null), active), ReadingStateRejection.INVALID)
            rejection(decide(explicit(true).copy(session = s), active), ReadingStateRejection.INVALID)
            val wrong = when (val input = c.input) {
                is ReadingCompletionInput.Visual -> input.copy(current = input.current.copy(loadId = "other"))
                is ReadingCompletionInput.Audio -> input.copy(current = input.current.copy(playbackId = "other"))
                else -> error("Wrong fixture kind")
            }
            rejection(decide(c.copy(input = wrong), active), ReadingStateRejection.INVALID)
            val newer = bindReadingStateWriter(active, s.copy(id = "new"))
            rejection(decide(c, newer), ReadingStateRejection.STALE)
            fun advanced(seq: Long) = if (kind == ReadingStateWriterKind.VISUAL) active.copy(visual = active.visual!!.copy(sequence = seq))
                else active.copy(audio = active.audio!!.copy(sequence = seq))
            rejection(decide(c, advanced(1)), ReadingStateRejection.CONFLICT)
            rejection(decide(c, advanced(2)), ReadingStateRejection.STALE)
        }
    }

    @Test fun noChangeAndIneligibleInputsReserveNoReceiptOrSequence() {
        for (kind in ReadingStateWriterKind.entries) {
            val s = session(kind); val active = bindReadingStateWriter(record, s); val c = automatic(s)
            val ineligible = when (val input = c.input) {
                is ReadingCompletionInput.Visual -> {
                    rejection(decide(c.copy(input = input.copy(policy = AutomaticReadPolicy.Disabled)), active), ReadingStateRejection.NO_CHANGE)
                    input.copy(current = input.current.copy(userInputEnabled = false))
                }
                is ReadingCompletionInput.Audio -> input.copy(current = input.current.copy(playing = false))
                else -> error("Wrong fixture kind")
            }
            rejection(decide(c.copy(input = ineligible), active), ReadingStateRejection.INELIGIBLE)
            rejection(decide(c, active.copy(value = value.copy(status = ReadStatus.READ, firstReadAt = 17))), ReadingStateRejection.NO_CHANGE)
            val accepted = decide(c, active)
            assertNotNull(accepted.receipt)
            assertEquals(1L, if (kind == ReadingStateWriterKind.VISUAL) accepted.record!!.visual!!.sequence else accepted.record!!.audio!!.sequence)
        }
    }

    @Test fun unchangedAudioIsANoopButMissingFirstReadStillCommits() {
        val s = session(ReadingStateWriterKind.AUDIO)
        val known = bindReadingStateWriter(record.copy(value = value.copy(status = ReadStatus.READ, firstReadAt = 17)), s)
        rejection(decide(automatic(s), known), ReadingStateRejection.NO_CHANGE)
        val missing = bindReadingStateWriter(record.copy(value = value.copy(status = ReadStatus.READ)), s)
        val filled = decide(automatic(s), missing)
        assertEquals(100L, filled.record!!.value.firstReadAt); assertNotNull(filled.receipt)
        rejection(decide(automatic(s, 2, "repeat"), filled.record), ReadingStateRejection.NO_CHANGE)
    }

    @Test fun storageCodecsPreserveIsolatedUtf16AcrossUtf8Storage() {
        val unusual = key.copy(url = "/\uD800")
        val r = record.copy(value = value.copy(key = unusual))
        val encoded = encodeReadingStateRecord(r)
        assertEquals(r, decodeReadingStateRecord(String(encoded.toByteArray(Charsets.UTF_8), Charsets.UTF_8)))
        val receipt = ReadingStateReceipt("id", "inc", "a".repeat(64), value.copy(key = unusual), 1)
        val receiptJson = encodeReadingStateReceipt(receipt)
        assertEquals(receipt, decodeReadingStateReceipt(String(receiptJson.toByteArray(Charsets.UTF_8), Charsets.UTF_8)))
    }

    @Test fun protocolAndReceiptCodecsRoundTripWithoutInferringLegacyState() {
        val s = session(); val active = bindReadingStateWriter(record, s); val first = decide(automatic(s), active)
        for (r in listOf(record, active, first.record!!, record.copy(value = value.copy(status = ReadStatus.UNKNOWN, firstReadAt = -17))))
            assertEquals(r, decodeReadingStateRecord(encodeReadingStateRecord(r)))
        assertEquals(first.receipt, decodeReadingStateReceipt(encodeReadingStateReceipt(first.receipt!!)))
        val json = encodeReadingStateRecord(active)
        for (bad in listOf(json.replace("\"format\":1", "\"format\":2"), json.replace("\"revision\":0", "\"revision\":\"0\""),
            json.replace("\"sequence\":0", "\"sequence\":-1"), json.replace("UNREAD", "unknown"),
            json.replace("\"format\":1", "\"format\":1,\"extra\":0"),
            json.replace("\"source\":17", "\"source\":17,\"extra\":0"),
            json.replace("\"status\":\"UNREAD\"", "\"status\":\"UNREAD\",\"extra\":0"),
            json.replace("\"sequence\":0", "\"sequence\":0,\"extra\":0"),
            json.replace("\"presence\":\"ABSENT\"", "\"presence\":\"ABSENT\",\"extra\":0")))
            assertThrows(IllegalArgumentException::class.java) { decodeReadingStateRecord(bad) }
    }

    @Test fun receiptGrowthTracksExplicitIntentsNotRepeatedPlaybackCompletions() {
        val initial = session(ReadingStateWriterKind.AUDIO)
        var current = bindReadingStateWriter(record, initial); var count = 0
        fun apply(c: ReadingStateCommand) {
            val transition = decide(c, current)
            current = requireNotNull(transition.record); assertNotNull(transition.receipt); count++
        }
        apply(automatic(initial))
        repeat(32) { index ->
            val b = binding.copy(intentRevision = current.intentRevision)
            apply(explicit(false, "unread-$index", b))
            val s = session(ReadingStateWriterKind.AUDIO, binding.copy(intentRevision = current.intentRevision), "audio-$index")
            current = bindReadingStateWriter(current, s); apply(automatic(s, id = "complete-$index"))
            repeat(3) { rejection(decide(automatic(s, 2L + it, "noop-$index-$it"), current), ReadingStateRejection.NO_CHANGE) }
        }
        assertEquals(32L, current.intentRevision); assertEquals(65, count)
    }

    @Test fun fingerprintsAreDeterministicAndPreserveExactUtf16RequestDistinctions() {
        val c = explicit(true)
        assertEquals(fingerprintReadingStateCommand(c), fingerprintReadingStateCommand(c.copy()))
        assertNotEquals(fingerprintReadingStateCommand(c), fingerprintReadingStateCommand(c.copy(input = ReadingCompletionInput.MarkRead(key, 101))))
        assertNotEquals(fingerprintReadingStateCommand(c.copy(binding = binding.copy(scope = "\uD800"))),
            fingerprintReadingStateCommand(c.copy(binding = binding.copy(scope = "\uD801"))))
    }
}
