package io.grimoire.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderColorThemeTest {
    @Test fun unsetThemeFollowsTheSystem() {
        assertEquals(ReaderColorTheme.LIGHT, resolveReaderColorTheme(null, false))
        assertEquals(ReaderColorTheme.DARK, resolveReaderColorTheme(null, true))
    }

    @Test fun everySavedChoiceOverridesBothSystemModes() {
        ReaderColorTheme.entries.forEach { saved ->
            assertEquals(saved, resolveReaderColorTheme(saved, false))
            assertEquals(saved, resolveReaderColorTheme(saved, true))
        }
    }
}
