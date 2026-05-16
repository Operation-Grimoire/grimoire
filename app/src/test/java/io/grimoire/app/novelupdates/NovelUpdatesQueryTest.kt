package io.grimoire.app.novelupdates

import io.grimoire.app.data.novelupdates.searchVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelUpdatesQueryTest {

    @Test
    fun searchVariants_emptyForBlank() {
        assertTrue(searchVariants("   ").isEmpty())
    }

    @Test
    fun searchVariants_buildsProgressivelySimplerQueries() {
        val v = searchVariants("The Undetectable Strongest Job: Rule Breaker (LN)")

        assertEquals("The Undetectable Strongest Job: Rule Breaker (LN)", v.first())
        assertTrue("variants distinct", v.size == v.distinct().size)
        assertTrue("at most 7 variants", v.size <= 7)
        // Drops the parenthetical, the subtitle, and the leading article — these
        // are the forms that actually substring-match NU's canonical title.
        assertTrue(v.contains("The Undetectable Strongest Job: Rule Breaker"))
        assertTrue(v.contains("The Undetectable Strongest Job"))
        assertTrue(v.contains("Undetectable Strongest Job"))
        assertTrue("no blank variants", v.none { it.isBlank() })
    }
}
