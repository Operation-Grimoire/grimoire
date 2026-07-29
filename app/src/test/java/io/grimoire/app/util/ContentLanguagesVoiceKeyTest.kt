package io.grimoire.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentLanguagesVoiceKeyTest {

    @Test
    fun `iso codes pass through`() {
        assertEquals("en", ContentLanguages.voiceKey("en"))
        assertEquals("ar", ContentLanguages.voiceKey("AR"))
    }

    @Test
    fun `english names resolve to their code`() {
        assertEquals("en", ContentLanguages.voiceKey("English"))
        assertEquals("ar", ContentLanguages.voiceKey("arabic"))
        assertEquals("zh", ContentLanguages.voiceKey("Chinese"))
    }

    @Test
    fun `name and code produce the same key`() {
        assertEquals(ContentLanguages.voiceKey("Japanese"), ContentLanguages.voiceKey("ja"))
    }

    @Test
    fun `unrecognised tokens fall back to normalized raw`() {
        assertEquals("klingon", ContentLanguages.voiceKey(" Klingon "))
    }
}
