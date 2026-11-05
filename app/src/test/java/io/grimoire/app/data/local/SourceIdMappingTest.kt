package io.grimoire.app.data.local

import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.entity.NovelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIdMappingTest {
    private val sources = listOf(InstalledSourceIdentity(100, "https://source.test"))
    private fun novel(id: Long, sourceId: Long, url: String = "https://source.test/book") =
        NovelEntity(id = id, sourceId = sourceId, url = url, title = "Book")

    @Test fun `legacy ids resolve to the installed package before restore lookup`() {
        assertEquals(100L, resolveSourceId(7, "https://SOURCE.test/book", sources))
        assertEquals(100L, resolveSourceId(100, "/relative", sources))
    }

    @Test fun `known identities and local books are never reattributed by host`() {
        val installed = sources + InstalledSourceIdentity(200, "https://other.test")
        assertEquals(100L, resolveSourceId(100, "https://other.test/book", installed))
        assertEquals(LOCAL_SOURCE_ID, resolveSourceId(LOCAL_SOURCE_ID, "https://source.test/book", installed))
    }

    @Test fun `missing relative and ambiguous source mappings remain unresolved`() {
        assertNull(resolveSourceId(7, "/relative", sources))
        assertNull(resolveSourceId(7, "https://missing.test/book", sources))
        assertNull(resolveSourceId(7, "https://source.test/book",
            sources + InstalledSourceIdentity(200, "https://source.test/other")))
        assertNull(resolveSourceId(7, "https://source.test/book", emptyList()))
    }

    @Test fun `unresolved rows cannot mark migration complete while valid rows can move`() {
        val plan = planSourceIdMigration(listOf(novel(1, 7),
            novel(2, 8, "https://missing.test/book"), novel(3, LOCAL_SOURCE_ID, "epub:book")), sources)
        assertEquals(listOf(SourceIdChange(1, 100)), plan.changes)
        assertEquals(1, plan.unresolved)
        assertFalse(plan.complete)
    }

    @Test fun `canonical libraries can finish without changing local identities`() {
        val plan = planSourceIdMigration(listOf(novel(1, 100), novel(2, LOCAL_SOURCE_ID, "epub:book")), sources)
        assertTrue(plan.changes.isEmpty())
        assertTrue(plan.complete)
    }

    @Test fun `pre-existing duplicate pairs are preserved and remain pending`() {
        val plan = planSourceIdMigration(listOf(novel(1, 7), novel(2, 100)), sources)
        assertTrue(plan.changes.isEmpty())
        assertEquals(1, plan.unresolved)
        assertFalse(plan.complete)
    }

    @Test fun `colliding legacy aliases never choose one record to overwrite`() {
        val plan = planSourceIdMigration(listOf(novel(1, 7), novel(2, 8)), sources)
        assertTrue(plan.changes.isEmpty())
        assertEquals(2, plan.unresolved)
        assertFalse(plan.complete)
    }
}
