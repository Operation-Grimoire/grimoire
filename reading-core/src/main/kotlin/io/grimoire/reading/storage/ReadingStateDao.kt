package io.grimoire.reading.storage

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.state.*

data class ReadingStateBinding(val key: ChapterKey, val scope: String, val incarnation: String, val intentRevision: Long)
data class PreparedReadingState(val binding: ReadingStateBinding, val value: ReadingCompletionState)
enum class ReadingStateWriterKind { VISUAL, AUDIO }
data class ReadingStateSession(
    val id: String, val binding: ReadingStateBinding, val content: ChapterStorageObservation,
    val document: CanonicalDocument, val kind: ReadingStateWriterKind,
)
data class ReadingStateWriter(
    val id: String, val scope: String, val content: ChapterStorageObservation, val logicalRevision: String,
    val sequence: Long,
)
data class ReadingStateRecord(
    val incarnation: String, val intentRevision: Long, val value: ReadingCompletionState,
    val visual: ReadingStateWriter? = null, val audio: ReadingStateWriter? = null,
)
data class ReadingStateCommand(
    val id: String, val binding: ReadingStateBinding, val input: ReadingCompletionInput,
    val session: ReadingStateSession?, val sequence: Long,
)
data class ReadingStateReceipt(
    val commandId: String, val incarnation: String, val fingerprint: String,
    val valueAtCommit: ReadingCompletionState, val intentRevisionAtCommit: Long,
)
enum class ReadingStateRejection { STALE, CONFLICT, INVALID, INELIGIBLE, NO_CHANGE }
sealed interface ReadingStateCommit {
    /** A replay confirms the old operation; only current describes the current stored state. */
    data class Committed(val valueAtCommit: ReadingCompletionState, val current: ReadingCompletionState,
        val intentRevision: Long, val replayed: Boolean) : ReadingStateCommit
    data class NotApplied(val reason: ReadingStateRejection) : ReadingStateCommit
}
data class ReadingStateTransition(val result: ReadingStateCommit, val record: ReadingStateRecord?, val receipt: ReadingStateReceipt?)

interface ReadingStateDao {
    /** Allocates protocol identity only for an existing chapter; never changes legacy reading values. */
    suspend fun prepare(key: ChapterKey): PreparedReadingState?
    suspend fun register(binding: ReadingStateBinding, content: ChapterStorageObservation, document: CanonicalDocument,
        kind: ReadingStateWriterKind): ReadingStateSession?
    /** Retire only this exact current writer; preserve value, intent revision, receipts and the other kind. */
    suspend fun retire(session: ReadingStateSession): Boolean
    /**
     * Commit completion values, protocol and receipt atomically. Word-count backfill, position and
     * opened history are separate platform-facade outcomes; Committed does not acknowledge them.
     */
    suspend fun commit(command: ReadingStateCommand): ReadingStateCommit
    suspend fun read(key: ChapterKey): ReadingCompletionState?
}

/** A null result is an idempotent no-op, including an old handle superseded by a newer writer. */
fun retireReadingStateWriter(record: ReadingStateRecord?, session: ReadingStateSession, scopeCurrent: Boolean): ReadingStateRecord? {
    if (record == null || !scopeCurrent || session.binding.key != record.value.key ||
        session.binding.incarnation != record.incarnation || session.binding.intentRevision != record.intentRevision) return null
    val writer = if (session.kind == ReadingStateWriterKind.VISUAL) record.visual else record.audio
    if (writer == null || writer.id != session.id || writer.scope != session.binding.scope ||
        writer.content != session.content || writer.logicalRevision != session.document.logicalRevision ||
        session.document.key != session.binding.key) return null
    return if (session.kind == ReadingStateWriterKind.VISUAL) record.copy(visual = null) else record.copy(audio = null)
}

fun bindReadingStateWriter(record: ReadingStateRecord, session: ReadingStateSession): ReadingStateRecord {
    require(session.binding.key == record.value.key && session.binding.incarnation == record.incarnation &&
        session.binding.intentRevision == record.intentRevision && session.id.isNotBlank() && session.binding.scope.isNotBlank() &&
        canRegisterReadingSession(session.content, session.document) && session.content.key == record.value.key)
    val writer = ReadingStateWriter(session.id, session.binding.scope, session.content, session.document.logicalRevision, 0)
    return when (session.kind) {
        ReadingStateWriterKind.VISUAL -> record.copy(visual = writer)
        ReadingStateWriterKind.AUDIO -> record.copy(audio = writer)
    }
}

/** Pure transactional decision. Callers supply current persisted rows and adapter-verified scope/content facts. */
fun decideReadingState(command: ReadingStateCommand, record: ReadingStateRecord?, receipt: ReadingStateReceipt?,
    scopeCurrent: Boolean, contentCurrent: Boolean): ReadingStateTransition {
    fun reject(reason: ReadingStateRejection) = ReadingStateTransition(ReadingStateCommit.NotApplied(reason), null, null)
    val binding = command.binding
    if (record == null || binding.key != record.value.key || binding.incarnation != record.incarnation) return reject(ReadingStateRejection.STALE)
    if (command.id.isBlank() || binding.scope.isBlank() || binding.incarnation.isBlank() || binding.intentRevision < 0 ||
        command.input.key != binding.key) return reject(ReadingStateRejection.INVALID)
    val fingerprint = fingerprintReadingStateCommand(command)
    if (receipt != null) {
        if (receipt.commandId != command.id || receipt.incarnation != record.incarnation || receipt.valueAtCommit.key != binding.key ||
            receipt.intentRevisionAtCommit > record.intentRevision ||
            receipt.valueAtCommit.status != (if (command.input is ReadingCompletionInput.MarkUnread) ReadStatus.UNREAD else ReadStatus.READ) ||
            (receipt.valueAtCommit.status == ReadStatus.READ && receipt.valueAtCommit.firstReadAt == null))
            return reject(ReadingStateRejection.INVALID)
        if (receipt.fingerprint != fingerprint) return reject(ReadingStateRejection.CONFLICT)
        return ReadingStateTransition(ReadingStateCommit.Committed(receipt.valueAtCommit, record.value, record.intentRevision, true), null, null)
    }
    if (!scopeCurrent) return reject(ReadingStateRejection.STALE)
    val automatic = command.input is ReadingCompletionInput.Visual || command.input is ReadingCompletionInput.Audio
    if (binding.intentRevision != record.intentRevision) return reject(if (automatic) ReadingStateRejection.STALE else ReadingStateRejection.CONFLICT)
    val session = command.session
    var writer: ReadingStateWriter? = null
    if (automatic) {
        if (session == null || command.sequence <= 0) return reject(ReadingStateRejection.INVALID)
        writer = if (session.kind == ReadingStateWriterKind.VISUAL) record.visual else record.audio
        if (!contentCurrent || session.binding != binding || writer == null || session.id != writer.id || binding.scope != writer.scope ||
            session.content != writer.content || session.document.logicalRevision != writer.logicalRevision ||
            !canRegisterReadingSession(session.content, session.document)) return reject(ReadingStateRejection.STALE)
        val input = command.input
        val matches = when (input) {
            is ReadingCompletionInput.Visual -> session.kind == ReadingStateWriterKind.VISUAL &&
                input.evidence.loadId == session.id && input.current.loadId == session.id &&
                input.current.observation == session.content && input.current.projection.document?.logicalRevision == writer.logicalRevision
            is ReadingCompletionInput.Audio -> session.kind == ReadingStateWriterKind.AUDIO &&
                input.evidence.playbackId == session.id && input.current.playbackId == session.id &&
                input.current.observation == session.content && input.current.document.logicalRevision == writer.logicalRevision
            else -> false
        }
        if (!matches) return reject(ReadingStateRejection.INVALID)
        if (command.sequence <= writer.sequence) return reject(if (command.sequence == writer.sequence) ReadingStateRejection.CONFLICT else ReadingStateRejection.STALE)
    } else if (session != null || command.sequence != 0L) return reject(ReadingStateRejection.INVALID)
    val decision = decideReadingCompletion(record.value, command.input)
    if (decision is ReadingCompletionDecision.Rejected) return reject(when (decision.reason) {
        CompletionRejection.INVALID, CompletionRejection.IDENTITY -> ReadingStateRejection.INVALID
        CompletionRejection.INELIGIBLE -> ReadingStateRejection.INELIGIBLE
    })
    if (decision !is ReadingCompletionDecision.Proposed || (automatic && decision.state == record.value))
        return reject(ReadingStateRejection.NO_CHANGE)
    if (!automatic && record.intentRevision == Long.MAX_VALUE) return reject(ReadingStateRejection.INVALID)
    val next = if (!automatic) record.copy(intentRevision = record.intentRevision + 1, value = decision.state, visual = null, audio = null)
        else if (session!!.kind == ReadingStateWriterKind.VISUAL) record.copy(value = decision.state, visual = writer!!.copy(sequence = command.sequence))
        else record.copy(value = decision.state, audio = writer!!.copy(sequence = command.sequence))
    val applied = ReadingStateReceipt(command.id, record.incarnation, fingerprint, next.value, next.intentRevision)
    return ReadingStateTransition(ReadingStateCommit.Committed(next.value, next.value, next.intentRevision, false), next, applied)
}
