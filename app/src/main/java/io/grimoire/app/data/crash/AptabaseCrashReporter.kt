package io.grimoire.app.data.crash

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.BuildConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AptabaseCrashReporter @Inject constructor(@param:ApplicationContext private val context: Context) {
    internal val queue = AptabaseErrorQueue(File(context.noBackupFilesDir, "aptabase_errors"))
    internal val configured = aptabaseErrorEndpoint(BuildConfig.APTABASE_APP_KEY) != null
    private val capture = AptabaseCrashCapture(queue, configured)
    private val sessionId = UUID.randomUUID().toString()
    @Volatile private var optedOutThisSession = false
    internal val permitsUpload: Boolean get() = !optedOutThisSession

    /** Called on the crashing thread: disk only; no SDK, WorkManager or network work. */
    fun captureCrash(throwable: Throwable) {
        capture.capture {
            buildAptabaseError(
                throwable,
                "${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA.take(8)})",
                Build.VERSION.RELEASE,
                sessionId,
                BuildConfig.DEBUG,
            )
        }
    }

    /** Apply an explicit choice immediately, before the async preference write. */
    @Synchronized
    fun selectCollection(enabled: Boolean) {
        optedOutThisSession = !enabled
        if (!enabled) {
            capture.disableAndClear()
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    /** Called off the main thread after the persisted preference is read. */
    @Synchronized
    fun applyPreference(enabled: Boolean) {
        val allowed = enabled && configured && permitsUpload
        capture.setEnabled(allowed)
        val workManager = WorkManager.getInstance(context)
        if (!allowed) {
            workManager.cancelUniqueWork(WORK_NAME)
            queue.clear()
        } else if (queue.pending().isNotEmpty()) {
            val request = OneTimeWorkRequestBuilder<AptabaseErrorWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    companion object { internal const val WORK_NAME = "aptabase-error-upload" }
}
