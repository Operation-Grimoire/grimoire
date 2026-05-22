package io.grimoire.app.data.tts

/**
 * A speech backend that speaks one utterance at a time. [TtsPlaybackManager] drives the
 * utterance queue and is engine-agnostic: it calls [speak] and reacts to [Listener]
 * callbacks. Utterance ids are opaque strings owned by the manager (they encode a
 * playback generation so stale callbacks after a stop/skip can be discarded).
 */
interface TtsEngine {
    val type: TtsEngineType

    /**
     * True if [resume] continues the current utterance from where [pause] left off.
     * When false the manager re-speaks the current utterance from its start on resume.
     */
    val canResumeMidUtterance: Boolean

    /** Prepares the engine. Returns false if it cannot be used (e.g. missing API key). */
    suspend fun init(): Boolean

    fun configure(config: TtsEngineConfig)

    fun speak(utteranceId: String, text: String)

    /** Optional warm-up of the next utterance to avoid gaps; no-op for the device engine. */
    fun prefetch(utteranceId: String, text: String) {}

    fun pause()
    fun resume()
    fun stop()
    fun release()

    var listener: Listener?

    interface Listener {
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onError(utteranceId: String, message: String)
    }
}
