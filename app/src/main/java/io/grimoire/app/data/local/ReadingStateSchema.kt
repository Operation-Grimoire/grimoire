package io.grimoire.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal fun installReadingStateTriggers(db: SupportSQLiteDatabase) {
    // No value-change condition: same-value explicit legacy read/unread must revoke old automatic work too.
    db.execSQL("CREATE TRIGGER IF NOT EXISTS reading_state_legacy AFTER UPDATE OF read,firstReadAt ON chapters " +
        "BEGIN DELETE FROM reading_states WHERE chapterId=NEW.id; END")
    db.execSQL("CREATE TRIGGER IF NOT EXISTS reading_state_chapter_identity AFTER UPDATE OF url,novelId ON chapters " +
        "WHEN OLD.url != NEW.url OR OLD.novelId != NEW.novelId BEGIN DELETE FROM reading_states WHERE chapterId=NEW.id; END")
    db.execSQL("CREATE TRIGGER IF NOT EXISTS reading_state_novel_identity AFTER UPDATE OF sourceId,url ON novels " +
        "WHEN OLD.sourceId != NEW.sourceId OR OLD.url != NEW.url " +
        "BEGIN DELETE FROM reading_states WHERE chapterId IN (SELECT id FROM chapters WHERE novelId=NEW.id); END")
}

internal val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reading_states (chapterId INTEGER NOT NULL, state TEXT NOT NULL, " +
            "PRIMARY KEY(chapterId), FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reading_state_receipts (chapterId INTEGER NOT NULL, commandId TEXT NOT NULL, receipt TEXT NOT NULL, " +
            "PRIMARY KEY(chapterId,commandId), FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        installReadingStateTriggers(db)
    }
}
