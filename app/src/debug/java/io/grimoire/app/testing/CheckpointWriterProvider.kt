package io.grimoire.app.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.room.Room
import androidx.room.RoomDatabase
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.local.DOWNLOAD_SCHEMA_CALLBACK
import io.grimoire.app.data.local.RoomReadingCheckpointDao
import io.grimoire.app.data.local.RoomSavedChapterDao
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.anchor.AnchorPosition
import io.grimoire.reading.anchor.AnchorPrecision
import io.grimoire.reading.anchor.CanonicalDocument
import io.grimoire.reading.anchor.ReadingAnchor
import io.grimoire.reading.decodeChapterContent
import io.grimoire.reading.storage.CheckpointCommand
import io.grimoire.reading.storage.CheckpointCommit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps a second Room connection open in a private process for the emulator fixture. */
class CheckpointWriterProvider : ContentProvider() {
    private val access = Mutex()
    private var openFixture: Pair<String, AppDatabase>? = null

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (Binder.getCallingUid() != Process.myUid()) throw SecurityException("App-private checkpoint fixture")
        check(BuildConfig.DEBUG && Build.HARDWARE in setOf("ranchu", "goldfish"))
        val name = databaseName(requireNotNull(arg))
        val context = requireNotNull(context)
        return runBlocking {
            access.withLock {
                check(openFixture?.first?.let { it == name } ?: true)
                when (method) {
                    "advance" -> {
                        check(openFixture == null && context.getDatabasePath(name).isFile)
                        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                            .addCallback(DOWNLOAD_SCHEMA_CALLBACK)
                            .build()
                        openFixture = name to db
                        advance(db)
                    }
                    "read" -> checkpoint(requireNotNull(openFixture).second)
                    "close" -> {
                        openFixture?.second?.close()
                        openFixture = null
                        Bundle.EMPTY
                    }
                    else -> throw IllegalArgumentException("Unknown checkpoint fixture command")
                }
            }
        }
    }

    private suspend fun advance(db: AppDatabase): Bundle {
        val saved = RoomSavedChapterDao(db)
        val dao = RoomReadingCheckpointDao(db, saved)
        val observed = saved.readObserved(target)
        val document = CanonicalDocument.fromPages(target, decodeChapterContent(requireNotNull(observed.saved).content))
        val session = requireNotNull(dao.register(requireNotNull(observed.observation), document))
        val previous = requireNotNull(session.checkpoint).position as AnchorPosition.Block
        val anchor = ReadingAnchor(target, document.logicalRevision,
            AnchorPosition.Block(1, document.blockFingerprint(1), 0, AnchorPrecision.BLOCK_START))
        check(dao.commit(CheckpointCommand(session, 1, anchor)) == CheckpointCommit.Committed(anchor, 1, false))
        return checkpoint(db).apply { putInt("previousOrdinal", previous.ordinal) }
    }

    private suspend fun checkpoint(db: AppDatabase) = Bundle().apply {
        putInt("pid", Process.myPid())
        putString("checkpoint", requireNotNull(db.readingCheckpointStateDao().find(1)).state)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        internal val target = ChapterKey(NovelKey(17, "/checkpoint-fixture"), "/target")

        internal fun databaseName(fixture: String): String {
            require(fixture.matches(Regex("[a-f0-9]{32}")))
            return "checkpoint-race-$fixture.db"
        }
    }
}
