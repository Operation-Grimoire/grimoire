package io.grimoire.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.grimoire.app.data.local.entity.ChapterDownloadStateEntity

@Dao
interface ChapterDownloadStateDao {
    @Query("SELECT * FROM chapter_download_state WHERE chapterId = :id")
    suspend fun find(id: Long): ChapterDownloadStateEntity?

    @Upsert suspend fun put(state: ChapterDownloadStateEntity)
}
