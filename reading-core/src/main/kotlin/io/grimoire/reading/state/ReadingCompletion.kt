package io.grimoire.reading.state

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.anchor.*
import io.grimoire.reading.storage.ChapterStorageObservation

enum class ReadStatus { UNKNOWN, UNREAD, READ }

/** UNKNOWN preserves the absence of historical desktop read state. Existing timestamps are retained verbatim. */
data class ReadingCompletionState(val key: ChapterKey, val status: ReadStatus, val firstReadAt: Long?)

/** Android's existing last-visible fraction, including decorations; unrelated to CanonicalProgress. */
class AndroidViewportV1 private constructor(val first: Int, val last: Int, val total: Int, val fraction: Float) {
    companion object {
        fun create(first: Int, last: Int, total: Int, fraction: Float): AndroidViewportV1? {
            if (total <= 0 || first !in 0 until total || last !in first until total ||
                !fraction.isFinite() || fraction !in 0f..1f || fraction != (last + 1).toFloat() / total) return null
            return AndroidViewportV1(first, last, total, fraction)
        }
    }
}

sealed interface AutomaticReadPolicy {
    data object Disabled : AutomaticReadPolicy
    data class Percent(val threshold: Int) : AutomaticReadPolicy { init { require(threshold in 50..100) } }
    data class ItemsFromEnd(val count: Int) : AutomaticReadPolicy { init { require(count in 0..20) } }
    data object AtEnd : AutomaticReadPolicy
}

fun shouldCompleteReading(viewport: AndroidViewportV1, policy: AutomaticReadPolicy): Boolean = when (policy) {
    AutomaticReadPolicy.Disabled -> false
    is AutomaticReadPolicy.Percent -> viewport.fraction >= policy.threshold / 100f
    is AutomaticReadPolicy.ItemsFromEnd -> viewport.total - 1 - viewport.last <= policy.count
    AutomaticReadPolicy.AtEnd -> viewport.last == viewport.total - 1
}

data class VisualCompletionEvidence(
    val loadId: String, val observation: ChapterStorageObservation, val logicalRevision: String, val viewport: AndroidViewportV1,
)

/** Caller-supplied current facts, not a permit. Future adapters must revalidate durable authority in their transaction. */
data class VisualCompletionContext(
    val loadId: String, val observation: ChapterStorageObservation, val projection: ReadingProjection,
    val userInputEnabled: Boolean, val observationCurrent: Boolean,
)

data class AudioCompletionEvidence(
    val playbackId: String, val observation: ChapterStorageObservation, val logicalRevision: String,
    val utteranceCount: Int, val completedUtterance: Int, val engineCompleted: Boolean,
)

/** Playback is independent of a visual reader. Matching these facts alone grants no storage authority. */
data class AudioCompletionContext(
    val playbackId: String, val observation: ChapterStorageObservation, val document: CanonicalDocument,
    val utteranceCount: Int, val playing: Boolean, val observationCurrent: Boolean,
)

sealed interface ReadingCompletionInput {
    val key: ChapterKey
    data class MarkRead(override val key: ChapterKey, val atMillis: Long) : ReadingCompletionInput
    data class MarkUnread(override val key: ChapterKey) : ReadingCompletionInput
    data class Next(override val key: ChapterKey, val loadedChapter: ChapterKey?, val hasSuccessor: Boolean, val atMillis: Long) : ReadingCompletionInput
    data class Visual(override val key: ChapterKey, val evidence: VisualCompletionEvidence, val current: VisualCompletionContext,
        val policy: AutomaticReadPolicy, val atMillis: Long) : ReadingCompletionInput
    data class Audio(override val key: ChapterKey, val evidence: AudioCompletionEvidence, val current: AudioCompletionContext,
        val atMillis: Long) : ReadingCompletionInput
}

enum class CompletionRejection { IDENTITY, INVALID, INELIGIBLE }

sealed interface ReadingCompletionDecision {
    /** Explicit same-value intents still produce a proposal: the future DAO must advance intent ordering. */
    data class Proposed(val state: ReadingCompletionState) : ReadingCompletionDecision
    data object Unchanged : ReadingCompletionDecision
    data class Rejected(val reason: CompletionRejection) : ReadingCompletionDecision
}

/** Pure proposal only. No intent revision, command receipt, persistence, history, position or clock is owned here. */
fun decideReadingCompletion(state: ReadingCompletionState, input: ReadingCompletionInput): ReadingCompletionDecision {
    fun reject(reason: CompletionRejection) = ReadingCompletionDecision.Rejected(reason)
    fun read(at: Long): ReadingCompletionDecision = if (at < 0) reject(CompletionRejection.INVALID)
        else ReadingCompletionDecision.Proposed(state.copy(status = ReadStatus.READ, firstReadAt = state.firstReadAt ?: at))
    if (input.key != state.key) return reject(CompletionRejection.IDENTITY)
    return when (input) {
        is ReadingCompletionInput.MarkRead -> read(input.atMillis)
        is ReadingCompletionInput.MarkUnread -> ReadingCompletionDecision.Proposed(state.copy(status = ReadStatus.UNREAD))
        is ReadingCompletionInput.Next -> if (!input.hasSuccessor || input.loadedChapter != state.key || state.status == ReadStatus.READ)
            ReadingCompletionDecision.Unchanged else read(input.atMillis)
        is ReadingCompletionInput.Visual -> {
            val current = input.current; val evidence = input.evidence; val doc = current.projection.document
            if (doc?.key != state.key || evidence.observation.key != state.key || current.observation.key != state.key)
                reject(CompletionRejection.IDENTITY)
            else if (current.loadId.isBlank() || evidence.loadId != current.loadId || evidence.observation != current.observation ||
                evidence.logicalRevision != doc.logicalRevision || evidence.viewport.total != current.projection.itemCount ||
                !current.userInputEnabled || !current.observationCurrent ||
                current.projection.body.none { doc.blocks[it.canonicalOrdinal].hasProgressUnit() }) reject(CompletionRejection.INELIGIBLE)
            else if (state.status == ReadStatus.READ || !shouldCompleteReading(evidence.viewport, input.policy)) ReadingCompletionDecision.Unchanged
            else read(input.atMillis)
        }
        is ReadingCompletionInput.Audio -> {
            val current = input.current; val evidence = input.evidence
            if (current.document.key != state.key || current.observation.key != state.key || evidence.observation.key != state.key)
                reject(CompletionRejection.IDENTITY)
            else if (current.playbackId.isBlank() || evidence.playbackId != current.playbackId || evidence.observation != current.observation ||
                evidence.logicalRevision != current.document.logicalRevision || !current.playing || !current.observationCurrent ||
                current.utteranceCount <= 0 || evidence.utteranceCount != current.utteranceCount ||
                evidence.completedUtterance != current.utteranceCount - 1 || !evidence.engineCompleted ||
                current.document.blocks.none { it is AnchorBlock.Text && it.text.isNotBlank() }) reject(CompletionRejection.INELIGIBLE)
            else read(input.atMillis)
        }
    }
}
