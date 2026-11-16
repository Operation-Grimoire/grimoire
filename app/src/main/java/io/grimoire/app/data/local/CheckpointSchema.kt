package io.grimoire.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Empty expansion only; legacy anchors, bodies, download state and history are untouched. */
internal val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reading_checkpoints (chapterId INTEGER NOT NULL, state TEXT NOT NULL, " +
            "PRIMARY KEY(chapterId), FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}
