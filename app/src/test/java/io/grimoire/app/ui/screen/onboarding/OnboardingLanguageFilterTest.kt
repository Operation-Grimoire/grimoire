package io.grimoire.app.ui.screen.onboarding

import io.grimoire.api.model.lang.Language
import io.grimoire.app.util.ContentLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLanguageFilterTest {

    @Test
    fun `blank query returns full selectable list`() {
        assertEquals(ContentLanguages.SELECTABLE, filterLanguages(ContentLanguages.SELECTABLE, ""))
        assertEquals(ContentLanguages.SELECTABLE, filterLanguages(ContentLanguages.SELECTABLE, "   "))
    }

    @Test
    fun `matches display name, native name, and ISO code case-insensitively`() {
        val japaneseByEnglish = filterLanguages(ContentLanguages.SELECTABLE, "japan")
        assertTrue(Language.JA in japaneseByEnglish)

        val japaneseByNative = filterLanguages(ContentLanguages.SELECTABLE, "日本")
        assertEquals(listOf(Language.JA), japaneseByNative)

        val koreanByCode = filterLanguages(ContentLanguages.SELECTABLE, " KO ")
        assertTrue(Language.KO in koreanByCode)
    }
}
