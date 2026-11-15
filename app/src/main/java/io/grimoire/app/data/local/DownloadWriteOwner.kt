package io.grimoire.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One protocol writer per app store. OS releases the lease on process death; never close during a drain. */
@Singleton
class DownloadWriteOwner(private val file: File) : Closeable {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.noBackupFilesDir, "download-owner.lock"))
    private val id = UUID.randomUUID().toString()
    private var channel: FileChannel? = null
    private var lease: FileLock? = null
    private var closed = false

    @Synchronized fun requireOwner(): String {
        check(!closed) { "Download writer is closed" }
        if (lease == null) {
            file.parentFile!!.mkdirs()
            val candidate = FileChannel.open(file.toPath(), CREATE, WRITE)
            try {
                val acquired = try { candidate.tryLock() } catch (_: OverlappingFileLockException) { null }
                check(acquired != null) { "Another download writer owns this store" }
                channel = candidate
                lease = acquired
            } catch (failure: Throwable) { candidate.close(); throw failure }
        }
        return id
    }

    @Synchronized override fun close() {
        closed = true
        try { lease?.release() } finally { channel?.close() }
        lease = null
        channel = null
    }
}
