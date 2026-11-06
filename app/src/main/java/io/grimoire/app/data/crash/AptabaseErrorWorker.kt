package io.grimoire.app.data.crash

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.preferences.AnalyticsPreferences
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class AptabaseErrorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reporter: AptabaseCrashReporter,
    private val preferences: AnalyticsPreferences,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val transport = AptabaseErrorTransport(BuildConfig.APTABASE_APP_KEY)
            val retry = drainAptabaseErrors(
                reporter.queue,
                allowed = {
                    val persisted = preferences.crashReportsEnabled.changes().first()
                    reporter.configured && persisted && reporter.permitsUpload
                },
                send = transport::send,
            )
            if (retry) Result.retry() else Result.success()
        } catch (_: IOException) {
            Result.retry()
        }
    }
}
