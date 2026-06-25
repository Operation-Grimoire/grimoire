package io.grimoire.app.util

import io.grimoire.api.model.lang.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentLanguagesTest {

    @Test
    fun `selectable excludes the sentinels`() {
        assertTrue(Language.MULTI !in ContentLanguages.SELECTABLE)
        assertTrue(Language.UNKNOWN !in ContentLanguages.SELECTABLE)
        assertTrue(Language.EN in ContentLanguages.SELECTABLE)
    }

    @Test
    fun `parse resolves iso codes`() {
        assertEquals(Language.EN, ContentLanguages.parse("en"))
        assertEquals(Language.PT, ContentLanguages.parse(" PT "))
    }

    @Test
    fun `parse falls back to legacy english names`() {
        assertEquals(Language.ES, ContentLanguages.parse("spanish"))
        assertEquals(Language.JA, ContentLanguages.parse("Japanese"))
    }

    @Test
    fun `parse returns null for blanks and gibberish`() {
        assertNull(ContentLanguages.parse(""))
        assertNull(ContentLanguages.parse("   "))
        assertNull(ContentLanguages.parse("klingon"))
    }

    @Test
    fun `serialize uses codes`() {
        assertEquals("en,ja", ContentLanguages.serialize(linkedSetOf(Language.EN, Language.JA)))
        assertEquals("", ContentLanguages.serialize(emptySet()))
    }

    @Test
    fun `deserialize reads codes and legacy names then round-trips to codes`() {
        // A legacy on-disk value (English names) still loads…
        val legacy = ContentLanguages.deserialize("spanish,korean")
        assertEquals(setOf(Language.ES, Language.KO), legacy)
        // …and re-serializing it heals the storage to codes.
        assertEquals("es,ko", ContentLanguages.serialize(linkedSetOf(Language.ES, Language.KO)))
    }

    @Test
    fun `deserialize drops unparseable tokens`() {
        assertEquals(setOf(Language.EN), ContentLanguages.deserialize("en,klingon"))
        assertEquals(emptySet<Language>(), ContentLanguages.deserialize(""))
    }
}
