package io.grimoire.reading.storage

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.anchor.*

/** Local write authority, separate from a portable anchor. Only the DAO issues the id. */
data class ReadingSession(val id: String, val observation: ChapterStorageObservation, val document: CanonicalDocument) {
    val binding: ReadingSessionBinding get() = ReadingSessionBinding(id, observation, document.logicalRevision)
}

data class ReadingSessionBinding(val id: String, val observation: ChapterStorageObservation, val logicalRevision: String)

/** Persisted protocol state. Registration resets ordering but preserves the last portable anchor. */
data class ReadingCheckpointState(val binding: ReadingSessionBinding, val sequence: Long = 0, val anchor: ReadingAnchor? = null)

data class CheckpointCommand(val session: ReadingSession, val sequence: Long, val anchor: ReadingAnchor)

enum class CheckpointRejection { STALE, INVALID, CONFLICT }

sealed interface CheckpointCommit {
    data class Committed(val anchor: ReadingAnchor, val sequence: Long, val replayed: Boolean) : CheckpointCommit
    data class NotApplied(val reason: CheckpointRejection) : CheckpointCommit
}

interface ReadingCheckpointDao {
    /** Allocates session identity, without source access or chapter identity allocation. Null grants no authority. */
    suspend fun register(observation: ChapterStorageObservation, document: CanonicalDocument): ReadingSession?
    /** Validate observation, binding and order inside the same transaction as persistence. */
    suspend fun commit(command: CheckpointCommand): CheckpointCommit
    /** Raw portable evidence only; reading a checkpoint never registers a session. */
    suspend fun read(key: ChapterKey): ReadingAnchor?
}

fun canRegisterReadingSession(observation: ChapterStorageObservation, document: CanonicalDocument): Boolean =
    observation.key == document.key && document.blocks.isNotEmpty()

/** Shared decision table, called by both adapters under their transaction/serialization boundary. */
fun decideCheckpoint(command: CheckpointCommand, state: ReadingCheckpointState?, observationCurrent: Boolean): CheckpointCommit {
    fun reject(reason: CheckpointRejection) = CheckpointCommit.NotApplied(reason)
    val session = command.session
    if (!observationCurrent || state?.binding != session.binding) return reject(CheckpointRejection.STALE)
    if (command.sequence <= 0 || session.id.isBlank() ||
        !canRegisterReadingSession(session.observation, session.document) ||
        command.anchor.logicalRevision != session.document.logicalRevision ||
        resolveReadingAnchor(command.anchor, session.document) !is AnchorResolution.Exact) {
        return reject(CheckpointRejection.INVALID)
    }
    if (command.sequence < state.sequence) return reject(CheckpointRejection.STALE)
    if (command.sequence == state.sequence) {
        if (command.anchor != state.anchor) return reject(CheckpointRejection.CONFLICT)
        return CheckpointCommit.Committed(command.anchor, command.sequence, true)
    }
    return CheckpointCommit.Committed(command.anchor, command.sequence, false)
}
