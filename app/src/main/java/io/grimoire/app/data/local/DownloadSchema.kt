package io.grimoire.app.data.local

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Triggers guard every existing SQL writer, including imports, backup restore and reconciliation. */
private fun installDownloadTriggers(db: SupportSQLiteDatabase) {
    val clear = "activeAttemptId=NULL, activeOwner=NULL, baseRevision=NULL, committedAttemptId=NULL, " +
        "digest=NULL, contentVersion=NULL, wordCount=NULL, revision=lower(hex(randomblob(16)))"
    for ((event, row) in listOf("INSERT" to "NEW", "UPDATE" to "NEW", "DELETE" to "OLD")) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS download_content_${event.lowercase()} AFTER $event ON chapter_content " +
            "BEGIN UPDATE chapter_download_state SET $clear, cacheRevision=lower(hex(randomblob(16))) WHERE chapterId=$row.chapterId; END")
    }
    db.execSQL("CREATE TRIGGER IF NOT EXISTS download_status_invalidation AFTER UPDATE OF downloadStatus ON chapters " +
        "WHEN NEW.downloadStatus NOT IN (2,6) BEGIN UPDATE chapter_download_state SET " +
        "activeAttemptId=NULL, activeOwner=NULL, baseRevision=NULL WHERE chapterId=NEW.id; END")
    db.execSQL("CREATE TRIGGER IF NOT EXISTS download_chapter_invalidation AFTER UPDATE OF url,novelId,locked ON chapters " +
        "WHEN OLD.url != NEW.url OR OLD.novelId != NEW.novelId OR OLD.locked != NEW.locked " +
        "BEGIN UPDATE chapter_download_state SET $clear WHERE chapterId=NEW.id; " +
        "UPDATE chapters SET downloadStatus=CASE WHEN downloadStatus IN (5,6) THEN 3 ELSE 0 END " +
        "WHERE id=NEW.id AND NEW.locked=1 AND downloadStatus IN (1,2,5,6); END")
    db.execSQL("CREATE TRIGGER IF NOT EXISTS download_novel_invalidation AFTER UPDATE OF sourceId,url ON novels " +
        "WHEN OLD.sourceId != NEW.sourceId OR OLD.url != NEW.url BEGIN UPDATE chapter_download_state SET $clear " +
        "WHERE chapterId IN (SELECT id FROM chapters WHERE novelId=NEW.id); END")
}

internal val DOWNLOAD_SCHEMA_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = installDownloadTriggers(db)
}

internal val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS chapter_download_state (chapterId INTEGER NOT NULL, " +
            "revision TEXT NOT NULL, activeAttemptId TEXT, activeOwner TEXT, baseRevision TEXT, " +
            "committedAttemptId TEXT, digest TEXT, contentVersion INTEGER, wordCount INTEGER, cacheRevision TEXT, " +
            "PRIMARY KEY(chapterId), FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chapter_download_state_activeAttemptId ON chapter_download_state(activeAttemptId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chapter_download_state_committedAttemptId ON chapter_download_state(committedAttemptId)")
        installDownloadTriggers(db)
    }
}
