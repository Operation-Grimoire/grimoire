package io.grimoire.app.data.tts

internal class TtsFocusResumePolicy {
    private var pausedByFocus = false

    fun transientLoss(isPlaying: Boolean): Boolean {
        if (!isPlaying) return false
        pausedByFocus = true
        return true
    }

    fun clear() { pausedByFocus = false }

    fun gain(): Boolean {
        val resume = pausedByFocus
        clear()
        return resume
    }
}
