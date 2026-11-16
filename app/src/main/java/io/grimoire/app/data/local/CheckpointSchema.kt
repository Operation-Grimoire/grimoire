package io.grimoire.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Identity changes retire portable evidence too; a new key must not inherit the old key's anchor. */
internal fun installCheckpointTriggers(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TRIGGER IF NOT EXISTS checkpoint_chapter_identity AFTER UPDATE OF url,novelId ON chapters " +
        "WHEN OLD.url != NEW.url OR OLD.novelId != NEW.novelId " +
        "BEGIN DELETE FROM reading_checkpoints WHERE chapterId=NEW.id; END")
    db.execSQL("CREATE TRIGGER IF NOT EXISTS checkpoint_novel_identity AFTER UPDATE OF sourceId,url ON novels " +
        "WHEN OLD.sourceId != NEW.sourceId OR OLD.url != NEW.url " +
        "BEGIN DELETE FROM reading_checkpoints WHERE chapterId IN (SELECT id FROM chapters WHERE novelId=NEW.id); END")
}

/** Empty expansion only; legacy anchors, bodies, download state and history are untouched. */
internal val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reading_checkpoints (chapterId INTEGER NOT NULL, state TEXT NOT NULL, " +
            "PRIMARY KEY(chapterId), FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        installCheckpointTriggers(db)
    }
}
