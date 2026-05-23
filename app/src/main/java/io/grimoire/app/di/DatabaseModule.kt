package io.grimoire.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.RepoDao
import io.grimoire.app.data.local.dao.UpdateIssueDao
import javax.inject.Singleton

private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE library_updates ADD COLUMN unlockedFromLocked INTEGER NOT NULL DEFAULT 0"
        )
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE library_updates ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS library_updates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                novelId INTEGER NOT NULL,
                sourcePackage TEXT NOT NULL,
                novelUrl TEXT NOT NULL,
                novelTitle TEXT NOT NULL,
                novelThumbnailUrl TEXT,
                chapterUrl TEXT NOT NULL,
                chapterName TEXT NOT NULL,
                chapterNumber REAL NOT NULL DEFAULT -1,
                foundAt INTEGER NOT NULL,
                FOREIGN KEY(novelId) REFERENCES novels(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_updates_novelId ON library_updates (novelId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS update_issues (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                novelId INTEGER NOT NULL,
                sourcePackage TEXT NOT NULL,
                novelUrl TEXT NOT NULL,
                novelTitle TEXT NOT NULL,
                severity INTEGER NOT NULL,
                message TEXT NOT NULL,
                occurredAt INTEGER NOT NULL,
                FOREIGN KEY(novelId) REFERENCES novels(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_update_issues_novelId ON update_issues (novelId)")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE novels ADD COLUMN language TEXT")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE novels ADD COLUMN rating REAL")
        db.execSQL("ALTER TABLE novels ADD COLUMN ratingCount INTEGER")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN firstReadAt INTEGER")
        db.execSQL("ALTER TABLE chapters ADD COLUMN wordCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE chapters SET firstReadAt = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE read = 1"
        )
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE novels ADD COLUMN lastReadAt INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN queueOrder INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN downloadStatus INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chapters ADD COLUMN downloadedContent TEXT")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN readProgress REAL NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                `order` INTEGER NOT NULL DEFAULT 0,
                isDefault INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO categories (name, `order`, isDefault) VALUES ('Reading', 0, 1)")
        db.execSQL("ALTER TABLE novels ADD COLUMN categoryId INTEGER")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE novels ADD COLUMN chapterSortOrder INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE novels ADD COLUMN author TEXT")
        db.execSQL("ALTER TABLE novels ADD COLUMN genres TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS repos (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                indexUrl TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                addedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_repos_indexUrl ON repos (indexUrl)")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "grimoire.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
            .build()

    @Provides fun provideNovelDao(db: AppDatabase): NovelDao = db.novelDao()
    @Provides fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()
    @Provides fun provideRepoDao(db: AppDatabase): RepoDao = db.repoDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideLibraryUpdateDao(db: AppDatabase): LibraryUpdateDao = db.libraryUpdateDao()
    @Provides fun provideUpdateIssueDao(db: AppDatabase): UpdateIssueDao = db.updateIssueDao()
}
