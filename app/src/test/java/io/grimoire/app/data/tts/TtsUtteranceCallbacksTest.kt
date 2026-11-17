package io.grimoire.app.data.tts

import org.junit.Assert.*
import org.junit.Test

class TtsUtteranceCallbacksTest {
    @Test fun duplicateAndReplacedUtterancesCannotAdvancePlayback() {
        val callbacks = TtsUtteranceCallbacks()
        callbacks.speak("4:0:0")
        callbacks.clear()
        callbacks.speak("4:0:1")
        assertNull(callbacks.finish("4:0:0", playing = true, atMillis = 100))
        val accepted = requireNotNull(callbacks.finish("4:0:1", playing = true, atMillis = 200))
        assertTrue(accepted.completedWhilePlaying)
        assertEquals(200L, accepted.atMillis)
        assertNull(callbacks.finish("4:0:1", playing = true, atMillis = 300))
        assertSame(accepted, callbacks.finished)
        callbacks.clear()
        assertNull(callbacks.finish("4:0:1", playing = true, atMillis = 400))
    }

    @Test fun pauseCompletionCanAdvanceTransportWithoutCertifyingReading() {
        val callbacks = TtsUtteranceCallbacks()
        callbacks.speak("6:1:0")
        val paused = requireNotNull(callbacks.finish("6:1:0", playing = false, atMillis = 100))
        assertFalse(paused.completedWhilePlaying)
        assertNull(callbacks.finish("6:1:0", playing = true, atMillis = 200))
        assertSame(paused, callbacks.finished)
    }
}
