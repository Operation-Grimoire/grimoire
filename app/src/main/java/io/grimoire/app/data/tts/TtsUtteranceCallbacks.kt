package io.grimoire.app.data.tts

/** Transport can advance a completion kept during pause. Only a playing callback certifies reading. */
internal class TtsUtteranceCallbacks {
    data class Finished(val id: String, val atMillis: Long, val completedWhilePlaying: Boolean)

    private var active: String? = null
    var finished: Finished? = null
        private set

    fun speak(id: String) {
        active = id
        finished = null
    }

    fun clear() {
        active = null
        finished = null
    }

    fun isCurrent(id: String): Boolean = id == active && finished == null

    fun finish(id: String, playing: Boolean, atMillis: Long): Finished? {
        if (!isCurrent(id)) return null
        return Finished(id, atMillis, playing).also { finished = it }
    }
}
