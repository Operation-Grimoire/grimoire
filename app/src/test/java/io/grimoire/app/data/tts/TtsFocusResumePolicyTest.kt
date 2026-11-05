package io.grimoire.app.data.tts

import org.junit.Assert.*
import org.junit.Test

class TtsFocusResumePolicyTest {
    @Test fun transientInterruptionResumesExactlyOnce() {
        val policy = TtsFocusResumePolicy()
        assertTrue(policy.transientLoss(isPlaying = true))
        assertFalse(policy.transientLoss(isPlaying = false))
        assertTrue(policy.gain())
        assertFalse(policy.gain())
    }

    @Test fun userPauseOrStopDuringInterruptionPreventsResume() {
        val policy = TtsFocusResumePolicy()
        policy.transientLoss(isPlaying = true)
        policy.clear()
        assertFalse(policy.gain())
    }

    @Test fun interruptionCannotResumeAnAlreadyUserPausedSession() {
        val policy = TtsFocusResumePolicy()
        assertFalse(policy.transientLoss(isPlaying = false))
        assertFalse(policy.gain())
    }
}
