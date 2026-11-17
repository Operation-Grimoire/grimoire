package io.grimoire.app.ui.screen.reader

import io.grimoire.reading.anchor.ReadingProjection
import io.grimoire.reading.state.AndroidViewportV1
import io.grimoire.reading.state.AutomaticReadPolicy
import io.grimoire.reading.state.ReadingCompletionInput
import io.grimoire.reading.state.ReadingStateFacade
import io.grimoire.reading.state.VisualCompletionContext
import io.grimoire.reading.state.VisualCompletionEvidence
import io.grimoire.reading.state.shouldCompleteReading
import io.grimoire.reading.storage.ReadingStateCommand
import io.grimoire.reading.storage.ReadingStateDao
import io.grimoire.reading.storage.ReadingStateSession
import io.grimoire.reading.storage.ReadingStateWriterKind
import io.grimoire.reading.storage.canRegisterReadingSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Main-thread owner of one reader's visual authority. The DAO validates it again at commit. */
internal class ReaderVisualWriter(private val dao: ReadingStateDao, private val facade: ReadingStateFacade) {
    private val lifecycle = Mutex()
    private val retiring = linkedSetOf<ReadingStateSession>()
    private var target: ReaderContent? = null
    private var generation = 0L
    private var session: ReadingStateSession? = null
    private var submitted = false

    fun setTarget(content: ReaderContent?) {
        if (target === content) return
        session?.let(retiring::add)
        target = content
        generation++
        session = null
        submitted = false
    }

    fun invalidate(sessionId: String) {
        if (session?.id == sessionId) setTarget(null)
    }

    suspend fun synchronize(register: Boolean = true) = lifecycle.withLock {
        retireDetached()
        if (!register || session != null) return@withLock
        val content = target ?: return@withLock
        val observation = content.storageObservation ?: return@withLock
        val document = content.projection.document ?: return@withLock
        if (content.projection.body.isEmpty() || !canRegisterReadingSession(observation, document)) return@withLock
        val ticket = generation
        val prepared = dao.prepare(document.key) ?: return@withLock
        if (ticket != generation) return@withLock
        val registered = dao.register(prepared.binding, observation, document, ReadingStateWriterKind.VISUAL)
            ?: return@withLock
        if (ticket == generation) {
            session = registered
        } else {
            retiring.add(registered)
            retireDetached()
        }
    }

    private suspend fun retireDetached() {
        while (retiring.isNotEmpty()) {
            val detached = retiring.first()
            dao.retire(detached)
            retiring.remove(detached)
        }
    }

    fun authority(projection: ReadingProjection): ReadingStateSession? =
        session?.takeIf { target?.projection === projection && !submitted }

    fun capture(
        observed: ReadingStateSession,
        projection: ReadingProjection,
        viewport: AndroidViewportV1,
        policy: AutomaticReadPolicy,
        atMillis: Long,
    ): ReadingStateCommand? {
        if (authority(projection) !== observed || !shouldCompleteReading(viewport, policy)) return null
        val key = observed.binding.key
        val evidence = VisualCompletionEvidence(observed.id, observed.content, observed.document.logicalRevision, viewport)
        val current = VisualCompletionContext(observed.id, observed.content, projection,
            userInputEnabled = true, observationCurrent = true)
        return facade.prepareAutomatic(observed, ReadingCompletionInput.Visual(key, evidence, current, policy, atMillis), 1)
            .also { submitted = true }
    }
}
