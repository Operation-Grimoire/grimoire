package io.grimoire.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.RepoDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.RepoEntity

@Database(
    entities = [NovelEntity::class, ChapterEntity::class, RepoEntity::class, CategoryEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun repoDao(): RepoDao
    abstract fun categoryDao(): CategoryDao
}
