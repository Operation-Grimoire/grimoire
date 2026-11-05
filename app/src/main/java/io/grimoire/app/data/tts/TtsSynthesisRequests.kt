package io.grimoire.app.data.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Accessed on the playback dispatcher; prefetch and speak await the same work. */
internal class TtsSynthesisRequests(private val scope: CoroutineScope) {
    private val pending = mutableMapOf<String, Deferred<ByteArray>>()

    fun get(id: String, load: suspend () -> ByteArray): Deferred<ByteArray> =
        pending.getOrPut(id) { scope.async { load() } }

    fun consumed(id: String, request: Deferred<ByteArray>) {
        if (pending[id] === request) pending.remove(id)
    }

    fun clear() {
        pending.values.forEach { it.cancel() }
        pending.clear()
    }
}

internal class TtsAudioPlayback {
    enum class Resume { WAIT, START, COMPLETE }
    private var paused = false
    private var ready = false
    private var completionPending = false

    fun reset() { paused = false; ready = false; completionPending = false }
    fun pause() { paused = true }
    fun prepared(): Boolean { ready = true; return !paused }
    fun completed(): Boolean { ready = false; completionPending = paused; return !paused }

    fun resume(): Resume {
        paused = false
        if (completionPending) {
            completionPending = false
            return Resume.COMPLETE
        }
        return if (ready) Resume.START else Resume.WAIT
    }
}
