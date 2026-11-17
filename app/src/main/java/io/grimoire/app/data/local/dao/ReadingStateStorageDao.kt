package io.grimoire.app.data.local.dao

import androidx.room.*
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.ReadingStateEntity
import io.grimoire.app.data.local.entity.ReadingStateReceiptEntity

@Dao
interface ReadingStateStorageDao {
    @Query("SELECT * FROM chapters WHERE id IN (:ids)")
    suspend fun selectedChapters(ids: List<Long>): List<ChapterEntity>

    @Query("SELECT * FROM reading_states WHERE chapterId=:id") suspend fun find(id: Long): ReadingStateEntity?
    @Query("SELECT * FROM reading_state_receipts WHERE chapterId=:id AND commandId=:command")
    suspend fun receipt(id: Long, command: String): ReadingStateReceiptEntity?
    @Query("DELETE FROM reading_state_receipts WHERE chapterId=:id") suspend fun clearReceipts(id: Long)
    @Upsert suspend fun put(state: ReadingStateEntity)
    @Upsert suspend fun putReceipt(receipt: ReadingStateReceiptEntity)
    @Query("UPDATE chapters SET read=:read, firstReadAt=:firstReadAt WHERE id=:id")
    suspend fun setValues(id: Long, read: Boolean, firstReadAt: Long?)
}
