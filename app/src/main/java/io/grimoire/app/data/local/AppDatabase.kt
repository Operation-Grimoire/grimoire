package io.grimoire.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.RepoDao
import io.grimoire.app.data.local.dao.UpdateIssueDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.LibraryUpdateEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.data.local.entity.UpdateIssueEntity

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        RepoEntity::class,
        CategoryEntity::class,
        LibraryUpdateEntity::class,
        UpdateIssueEntity::class,
    ],
    version = 17,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun repoDao(): RepoDao
    abstract fun categoryDao(): CategoryDao
    abstract fun libraryUpdateDao(): LibraryUpdateDao
    abstract fun updateIssueDao(): UpdateIssueDao
}
