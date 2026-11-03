package io.grimoire.app.data.local

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Wraps Room's open helper so [moveChapterContentInBatches] runs on the database
 * file before Room opens it (and so before migration 31 → 32 runs). The work happens
 * on whichever thread first asks Room for a connection, which Room keeps off the main
 * thread; `create` itself (called from `Room.build()`) does nothing.
 *
 * Only upgrades from v30/v31 are pre-passed: those migrations never rebuild
 * `chapters`, so creating `chapter_content` ahead of them is safe. Older installs
 * fall back to the single-transaction move inside the migration.
 *
 * Failure is logged, not thrown: the migration still moves whatever is left, so a
 * failed pre-pass costs disk headroom, never data.
 */
internal class ChapterContentPrepassFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val helper = delegate.create(configuration)
        val dbName = configuration.name ?: return helper
        val context = configuration.context
        return object : SupportSQLiteOpenHelper by helper {
            @Volatile private var prepared = false

            private fun prepare() {
                if (prepared) return
                synchronized(this) {
                    if (prepared) return
                    runPrepass(context.getDatabasePath(dbName).path)
                    prepared = true
                }
            }

            override val writableDatabase: SupportSQLiteDatabase
                get() = prepare().let { helper.writableDatabase }

            override val readableDatabase: SupportSQLiteDatabase
                get() = prepare().let { helper.readableDatabase }
        }
    }

    private fun runPrepass(path: String) {
        if (!java.io.File(path).exists()) return
        runCatching {
            SQLiteDatabase.openDatabase(
                path,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
            ).use { db ->
                if (db.version !in PREPASS_FROM_VERSIONS) return
                val executor = FrameworkSqlExecutor(db)
                val start = System.currentTimeMillis()
                val moved = executor.withSecureDeleteOff { moveChapterContentInBatches(executor) }
                if (moved > 0) {
                    Log.i(TAG, "moved $moved chapter bodies in ${System.currentTimeMillis() - start} ms")
                }
            }
        }.onFailure { Log.w(TAG, "chapter content pre-pass failed; migration will finish it", it) }
    }

    private companion object {
        const val TAG = "ChapterContentPrepass"
        val PREPASS_FROM_VERSIONS = 30..31
    }
}

/**
 * Runs [block] with `secure_delete` off. Android builds SQLite with it on, which
 * zero-fills every freed page, writing each moved chapter's old pages into the WAL a
 * second time. The freed bytes are chapter text the app keeps anyway, so there is
 * nothing to scrub. The previous setting is restored afterwards.
 */
internal fun <T> SqlExecutor.withSecureDeleteOff(block: () -> T): T {
    val previous = queryLongs("PRAGMA secure_delete").firstOrNull() ?: 0L
    queryLongs("PRAGMA secure_delete = 0")
    return try {
        block()
    } finally {
        queryLongs("PRAGMA secure_delete = $previous")
    }
}

/** [SqlExecutor] over a raw framework connection; [inTransaction] commits per call. */
internal class FrameworkSqlExecutor(private val db: SQLiteDatabase) : SqlExecutor {
    override fun exec(sql: String, args: Array<Any?>) = db.execSQL(sql, args)

    override fun queryLongs(sql: String, args: Array<Any?>): List<Long?> =
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(if (c.isNull(0)) null else c.getLong(0)) }
        }

    override fun <T> inTransaction(block: () -> T): T {
        db.beginTransaction()
        try {
            return block().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }
}

/** [SqlExecutor] over Room's migration connection, which is already in a transaction. */
internal class SupportSqlExecutor(private val db: SupportSQLiteDatabase) : SqlExecutor {
    override fun exec(sql: String, args: Array<Any?>) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args)
    }

    override fun queryLongs(sql: String, args: Array<Any?>): List<Long?> =
        db.query(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(if (c.isNull(0)) null else c.getLong(0)) }
        }

    override fun <T> inTransaction(block: () -> T): T = block()
}
