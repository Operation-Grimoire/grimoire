package io.grimoire.reading.anchor

import io.grimoire.reading.storage.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Same-platform legacy viewport only. Never converted into a certified anchor or layout hint. */
data class LegacyReaderViewport(val index: Int, val offset: Int)
data class ReaderViewport(val index: Int, val offset: Int = 0, val end: Boolean = false)

enum class CheckpointNotice(val message: String) {
    LEGACY("Using an older saved reading position."),
    RELOCATED("Chapter changed. Restored the matching paragraph."),
    CHANGED("Chapter changed. Showing the beginning."),
    HIDDEN("Saved position is hidden here. Showing the next visible section."),
    COARSE("Restored to the start of the saved paragraph."),
    INVALID("Saved reading position is invalid. It has been retained."),
    UNSUPPORTED("Saved reading position uses an unsupported format. It has been retained."),
    IDENTITY("Saved reading position belongs to another chapter. It has been retained."),
    EMPTY("This chapter has no visible content."),
    RELOAD("Reload this chapter to save your reading position."),
}

data class ReaderCheckpointRestore(val viewport: ReaderViewport, val notice: CheckpointNotice?, val logical: AnchorResolution? = null)

/** Semantic rejection is terminal for those sessions, not a retryable IO failure. Stored evidence stays intact. */
class ReaderCheckpointRejected(val revokedLoads: List<ReaderCheckpointLoad>) :
    IllegalStateException("Reading position was rejected. Reload the chapter.")

/** One immutable published load. Mutable authority/capture state belongs to its coordinator. */
class ReaderCheckpointLoad internal constructor(
    val projection: ReadingProjection, val restore: ReaderCheckpointRestore,
    internal val session: ReadingSession?, internal val owner: ReaderCheckpoints,
) {
    internal var enabled = false
    internal var retired = false
    internal var revoked = false
    internal var sequence = 0L
}

/** Shared capture, restoration and newest-pending flush protocol; UI and legacy policy stay in hosts. */
class ReaderCheckpoints(private val dao: ReadingCheckpointDao) {
    private val lock = Any()
    private val writes = Mutex()
    private val pending = linkedMapOf<ReaderCheckpointLoad, CheckpointCommand>()
    private var active: ReaderCheckpointLoad? = null
    private var epoch = 0L

    fun retire() { synchronized(lock) { active?.retired = true; active = null; epoch++ } }

    suspend fun open(projection: ReadingProjection, observation: ChapterStorageObservation?, legacy: LegacyReaderViewport?): ReaderCheckpointLoad {
        val ticket = synchronized(lock) { retire(); epoch }
        return writes.withLock {
            flushPending()
            val document = projection.document
            val session = if (document != null && observation != null) dao.register(observation, document) else null
            val anchor = if (session != null) session.checkpoint else document?.let { dao.read(it.key) }
            val restore = restoreReaderCheckpoint(projection, anchor, legacy)
            val load = ReaderCheckpointLoad(projection, if (observation != null && session == null && restore.notice != CheckpointNotice.EMPTY)
                restore.copy(notice = CheckpointNotice.RELOAD) else restore, session, this)
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                if (ticket != epoch) throw CancellationException("Reader load was retired")
                active = load
            }
            load
        }
    }

    /** Explicit input/position selection, after restoration. Layout and programmatic restoration do not call this. */
    fun userPosition(load: ReaderCheckpointLoad) = synchronized(lock) {
        if (active === load && !load.retired && !load.revoked) load.enabled = true
    }

    fun acceptsCapture(load: ReaderCheckpointLoad): Boolean = synchronized(lock) {
        active === load && !load.retired && !load.revoked && load.enabled
    }

    fun canObserve(load: ReaderCheckpointLoad): Boolean = synchronized(lock) {
        active === load && !load.retired && !load.revoked
    }

    /** Portable restoration cannot rewrite compatibility position or trigger completion before input either. */
    fun canRecordPosition(load: ReaderCheckpointLoad): Boolean = synchronized(lock) {
        canObserve(load) && load.enabled && load.projection.body.isNotEmpty() && load.restore.notice != CheckpointNotice.RELOAD && load.restore.notice != CheckpointNotice.EMPTY
    }

    fun isRevoked(load: ReaderCheckpointLoad): Boolean = synchronized(lock) { load.revoked }

    fun revoke(load: ReaderCheckpointLoad) { synchronized(lock) {
        if (load.owner === this) {
            load.revoked = true; load.retired = true; pending.remove(load)
            if (active === load) { active = null; epoch++ }
        }
    } }

    /** Resume only a failed close's unchanged view, retaining the original session and pending sequence. */
    fun resume(load: ReaderCheckpointLoad) = synchronized(lock) {
        if (load.owner === this && active == null && !load.revoked) { epoch++; load.retired = false; active = load }
    }

    fun capture(load: ReaderCheckpointLoad, index: Int): ReadingAnchor? = synchronized(lock) {
        if (!acceptsCapture(load)) return null
        val session = load.session ?: return null
        val anchor = load.projection.capture(index) ?: return null
        check(load.sequence < Long.MAX_VALUE) { "Reading sequence exhausted; reload the chapter" }
        pending[load] = CheckpointCommand(session, ++load.sequence, anchor)
        anchor
    }

    /** A failed/uncertain commit retains the newest pending command; an older reply cannot clear it. */
    suspend fun flush() = writes.withLock { flushPending() }

    private suspend fun flushPending(): List<ReaderCheckpointLoad> {
        val revoked = mutableListOf<ReaderCheckpointLoad>()
        var rejected = false
        val snapshot = synchronized(lock) { pending.toList() }
        for ((load, command) in snapshot) {
            val result = dao.commit(command)
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                when (result) {
                    is CheckpointCommit.Committed -> if (pending[load] == command) pending.remove(load)
                    is CheckpointCommit.NotApplied -> {
                        if (result.reason != CheckpointRejection.STALE) rejected = true
                        // Terminal semantic rejection cannot poison every future open/flush.
                        load.revoked = true; pending.remove(load)
                        revoked += load
                    }
                }
            }
        }
        if (rejected) throw ReaderCheckpointRejected(revoked)
        return revoked
    }
}

/** Restore is a recommendation, never a capture or a rewrite of retained evidence. */
fun restoreReaderCheckpoint(projection: ReadingProjection, anchor: ReadingAnchor?, legacy: LegacyReaderViewport?): ReaderCheckpointRestore {
    if (anchor == null) {
        if (projection.body.isEmpty()) return ReaderCheckpointRestore(ReaderViewport(0), CheckpointNotice.EMPTY)
        if (legacy == null) return ReaderCheckpointRestore(ReaderViewport(0), null)
        val index = legacy.index.coerceIn(0, (projection.itemCount - 1).coerceAtLeast(0))
        return ReaderCheckpointRestore(ReaderViewport(index, if (index == legacy.index) legacy.offset.coerceAtLeast(0) else 0), CheckpointNotice.LEGACY)
    }
    val resolved = projection.resolve(anchor) ?: return ReaderCheckpointRestore(ReaderViewport(0), CheckpointNotice.IDENTITY)
    val target = resolved.target as? ProjectionTarget.Item
    val notice = when (resolved.logical) {
        is AnchorResolution.Invalid -> CheckpointNotice.INVALID
        is AnchorResolution.UnsupportedVersion -> CheckpointNotice.UNSUPPORTED
        is AnchorResolution.IdentityMismatch -> CheckpointNotice.IDENTITY
        is AnchorResolution.NoContent -> CheckpointNotice.EMPTY
        is AnchorResolution.Unresolved -> CheckpointNotice.CHANGED
        else -> when {
            target == null -> CheckpointNotice.EMPTY
            target.presentationFallback -> CheckpointNotice.HIDDEN
            (target.position as? AnchorPosition.Block)?.textOffset?.let { it > 0 } == true -> CheckpointNotice.COARSE
            resolved.logical is AnchorResolution.Relocated -> CheckpointNotice.RELOCATED
            else -> null
        }
    }
    return ReaderCheckpointRestore(ReaderViewport(target?.renderedIndex ?: 0, end = target?.position == AnchorPosition.End), notice, resolved.logical)
}
