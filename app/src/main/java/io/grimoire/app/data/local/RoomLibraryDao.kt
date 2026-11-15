package io.grimoire.app.data.local

import androidx.room.withTransaction
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.reading.NovelKey
import io.grimoire.reading.storage.LibraryDao
import io.grimoire.reading.storage.LibraryEntry
import javax.inject.Inject

class RoomLibraryDao @Inject constructor(private val database: AppDatabase) : LibraryDao {
    override suspend fun find(key: NovelKey): LibraryEntry? =
        database.novelDao().getBySourceUrl(key.sourceId, key.url)?.toLibraryEntry()

    override suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry? = database.withTransaction {
        val dao = database.novelDao()
        if (dao.setLibraryMembership(key.sourceId, key.url, inLibrary) == 0) return@withTransaction null
        checkNotNull(dao.getBySourceUrl(key.sourceId, key.url)).toLibraryEntry()
    }
}

private fun NovelEntity.toLibraryEntry() = LibraryEntry(
    NovelKey(sourceId, url), effectiveTitle, favorite, effectiveAuthor, overrideDescription ?: description,
)
