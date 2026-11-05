package io.grimoire.app.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class GrimoireWaveTest {
    @Test fun phaseHoldsForOneTickAndWrapsAtTheWavePeriod() {
        assertEquals(grimoireWavePhase(0), grimoireWavePhase(99), 0f)
        assertEquals(1f / 18f, grimoireWavePhase(100), 0f)
        assertEquals(grimoireWavePhase(0), grimoireWavePhase(1800), 0f)
        assertEquals(grimoireWavePhase(900), grimoireWavePhase(2700), 0f)
    }
}
