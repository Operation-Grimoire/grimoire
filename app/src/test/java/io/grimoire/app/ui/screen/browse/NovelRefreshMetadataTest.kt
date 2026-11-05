package io.grimoire.app.ui.screen.browse

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import org.junit.Assert.*
import org.junit.Test

class NovelRefreshMetadataTest {
    @Test fun refreshedNovelRetainsPersonalRatingAndReaderAlignment() {
        val novel = Novel(url = "book", title = "New source title", language = Language.UNKNOWN, rating = 7.5f)
        val entity = novel.toEntity(sourceId = 7, existingId = 42, favorite = false,
            userRating = 9, readerTextAlign = 3)
        assertEquals(9, entity.userRating)
        assertEquals(3, entity.readerTextAlign)
        assertEquals(7.5f, entity.rating)
        assertEquals("New source title", entity.title)
        assertEquals(42L, entity.id)
        assertFalse(entity.favorite)
    }
}
