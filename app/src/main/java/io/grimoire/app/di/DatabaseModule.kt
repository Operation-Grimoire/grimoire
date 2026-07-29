package io.grimoire.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.local.dao.BrowsingHistoryDao
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.NuBookmarkDao
import io.grimoire.app.data.local.dao.ReadingHistoryDao
import io.grimoire.app.data.local.dao.RepoDao
import io.grimoire.app.data.local.dao.TaskLogDao
import io.grimoire.app.data.local.dao.UpdateIssueDao
import javax.inject.Singleton

private val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Per-novel reader text alignment (ReaderTextAlign ordinal); 0 = AUTO.
        db.execSQL("ALTER TABLE novels ADD COLUMN readerTextAlign INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // User's own 1–10 rating, distinct from the source rating column. Nullable
        // (no default) so existing novels start unrated.
        db.execSQL("ALTER TABLE novels ADD COLUMN userRating INTEGER")
    }
}

private val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Reading + browsing history. No foreign keys — entries are denormalized snapshots
        // that must outlive chapter/novel pruning and cover non-library novels. The unique
        // indices back the REPLACE upsert (refresh openedAt on re-open).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reading_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sourcePackage TEXT NOT NULL,
                novelId INTEGER,
                novelUrl TEXT NOT NULL,
                novelTitle TEXT NOT NULL,
                novelThumbnailUrl TEXT,
                chapterUrl TEXT NOT NULL,
                chapterName TEXT NOT NULL,
                chapterNumber REAL NOT NULL,
                openedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_reading_history_sourcePackage_novelUrl_chapterUrl " +
                "ON reading_history (sourcePackage, novelUrl, chapterUrl)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS browsing_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sourcePackage TEXT NOT NULL,
                novelId INTEGER,
                novelUrl TEXT NOT NULL,
                novelTitle TEXT NOT NULL,
                novelThumbnailUrl TEXT,
                openedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_browsing_history_sourcePackage_novelUrl " +
                "ON browsing_history (sourcePackage, novelUrl)"
        )
    }
}

private val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The app must always have at least one (default) category. Installs upgraded
        // through v4→v5 already seeded one ('Reading'); fresh installs created directly
        // at a later schema version never ran that migration and have none. Insert one
        // only when absent so an existing — possibly renamed — default keeps its name.
        db.execSQL(
            """
            INSERT INTO categories (name, `order`, isDefault, isHidden)
            SELECT 'Default', 0, 1, 0
            WHERE NOT EXISTS (SELECT 1 FROM categories WHERE isDefault = 1)
            """.trimIndent()
        )
    }
}

/**
 * Fresh installs create the schema directly (Room runs no migrations on first create),
 * so the default category must be seeded here. Upgrades get it from [MIGRATION_26_27].
 */
private val SEED_DEFAULT_CATEGORY = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO categories (name, `order`, isDefault, isHidden) VALUES ('Default', 0, 1, 0)"
        )
    }
}

private val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // User cover + per-field metadata overrides (issues #151 / #152). All nullable,
        // so existing rows default to "no override" and keep updating from the source.
        db.execSQL("ALTER TABLE novels ADD COLUMN customCoverPath TEXT")
        db.execSQL("ALTER TABLE novels ADD COLUMN customCoverUrl TEXT")
        db.execSQL("ALTER TABLE novels ADD COLUMN overrideTitle TEXT")
        db.execSQL("ALTER TABLE novels ADD COLUMN overrideAuthor TEXT")
        db.execSQL("ALTER TABLE novels ADD COLUMN overrideDescription TEXT")
        db.execSQL("ALTER TABLE novels ADD COLUMN overrideStatus INTEGER")
        db.execSQL("ALTER TABLE novels ADD COLUMN overrideGenres TEXT")
    }
}

private val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Index the favorite flag: the library list (WHERE favorite = 1) and the
        // favorites-scoped chapter-stats join both filtered the whole novels table
        // without it. Name must match Room's generated index name for Index("favorite").
        db.execSQL("CREATE INDEX IF NOT EXISTS index_novels_favorite ON novels (favorite)")
    }
}

private val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nu_bookmarks (
                slug TEXT PRIMARY KEY NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                coverUrl TEXT,
                addedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE novels ADD COLUMN lastAccessedAt INTEGER NOT NULL DEFAULT 0")
        // Stamp existing favorites as freshly accessed so the first prune pass
        // can't touch them on a technicality; transient rows keep 0 and age out.
        db.execSQL(
            "UPDATE novels SET lastAccessedAt = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE favorite = 1"
        )
    }
}

private val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type INTEGER NOT NULL,
                completedAt INTEGER NOT NULL,
                success INTEGER NOT NULL,
                summary TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE novels ADD COLUMN autoDownloadNewChapters INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE novels ADD COLUMN notifyOnNewChapters INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE novels ADD COLUMN notifyOnNewLockedChapters INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN readAnchorItemIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chapters ADD COLUMN readAnchorItemOffset INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapters_downloadStatus ON chapters (downloadStatus)")
    }
}

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30)
            .addCallback(SEED_DEFAULT_CATEGORY)
            .build()

    @Provides fun provideNovelDao(db: AppDatabase): NovelDao = db.novelDao()
    @Provides fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()
    @Provides fun provideRepoDao(db: AppDatabase): RepoDao = db.repoDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideLibraryUpdateDao(db: AppDatabase): LibraryUpdateDao = db.libraryUpdateDao()
    @Provides fun provideUpdateIssueDao(db: AppDatabase): UpdateIssueDao = db.updateIssueDao()
    @Provides fun provideTaskLogDao(db: AppDatabase): TaskLogDao = db.taskLogDao()
    @Provides fun provideNuBookmarkDao(db: AppDatabase): NuBookmarkDao = db.nuBookmarkDao()
    @Provides fun provideReadingHistoryDao(db: AppDatabase): ReadingHistoryDao = db.readingHistoryDao()
    @Provides fun provideBrowsingHistoryDao(db: AppDatabase): BrowsingHistoryDao = db.browsingHistoryDao()
}
