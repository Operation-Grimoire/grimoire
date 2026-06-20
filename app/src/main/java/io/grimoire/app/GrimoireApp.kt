package io.grimoire.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import io.grimoire.app.data.backup.BackupScheduler
import io.grimoire.app.data.cache.CoverPreloader
import io.grimoire.app.data.crash.CrashLogStore
import io.grimoire.app.data.libraryupdate.LibraryUpdateScheduler
import io.grimoire.app.data.local.SourceIdMigrator
import io.grimoire.app.data.local.TransientNovelPruner
import io.grimoire.app.data.schedule.ScheduleMigrator
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.di.GitHubAuthorized
import io.grimoire.app.extension.repo.ExtensionRepository
import io.grimoire.api.network.NetworkContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class GrimoireApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var hiddenAuthManager: HiddenCategoriesAuthManager
    @Inject lateinit var coverPreloader: CoverPreloader
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var backupScheduler: BackupScheduler
    @Inject lateinit var libraryUpdateScheduler: LibraryUpdateScheduler
    @Inject lateinit var scheduleMigrator: ScheduleMigrator
    @Inject lateinit var sourceIdMigrator: SourceIdMigrator
    @Inject lateinit var extensionRepository: ExtensionRepository
    @Inject lateinit var transientNovelPruner: TransientNovelPruner
    @Inject lateinit var crashLogStore: CrashLogStore
    @Inject @GitHubAuthorized lateinit var imageHttpClient: OkHttpClient

    private var relockJob: Job? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        // Reuse the @GitHubAuthorized extension network stack so:
        //  - covers carry the WebView cf_clearance cookie + matching
        //    User-Agent (default stack), so Cloudflare-protected sites
        //    (e.g. Foxaholic) serve images rather than a challenge page;
        //  - extension icons hosted in private GitHub repos receive the
        //    Bearer token and the Accept: application/octet-stream the
        //    api.github.com asset endpoint requires.
        // Lambda form keeps client init off the main thread.
        .okHttpClient {
            // Some cover hosts (e.g. LibGen) hotlink-protect images and answer
            // 200 with an empty body unless the request carries a same-site
            // Referer. Add the image's own origin as Referer when none is set —
            // harmless for hosts that ignore it, and it makes protected covers
            // load without coupling the loader to any one source.
            imageHttpClient.newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    if (request.header("Referer") != null) {
                        chain.proceed(request)
                    } else {
                        chain.proceed(
                            request.newBuilder()
                                .header("Referer", "${request.url.scheme}://${request.url.host}/")
                                .build(),
                        )
                    }
                }
                .build()
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(filesDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024) // 256 MB
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        NetworkContext.init(this)
        coverPreloader.start()
        extensionRepository.checkForUpdatesOnLaunch()
        val channel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "Chapter Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Background chapter downloads" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                relockJob?.cancel()
                relockJob = null
            }

            override fun onStop(owner: LifecycleOwner) {
                // Relock after a grace window instead of immediately: onStop also fires
                // for app-driven excursions (the SAF picker the EPUB import opens, share
                // sheets, the in-app browser), and an instant lock would relock hidden
                // categories in the middle of the user's own flow. A real exit longer
                // than the window still locks. In-memory only on purpose — process death
                // always restarts locked.
                relockJob?.cancel()
                relockJob = ProcessLifecycleOwner.get().lifecycleScope.launch {
                    delay(RELOCK_GRACE_MS)
                    hiddenAuthManager.lock()
                }
                // Drop stale browse rows that aged past the grace window this session.
                ProcessLifecycleOwner.get().lifecycleScope.launch {
                    runCatching { transientNovelPruner.prune() }
                }
            }
        })

        val backupChannel = NotificationChannel(
            BACKUP_CHANNEL_ID,
            "Backups",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Scheduled local backups" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(backupChannel)

        val appUpdateChannel = NotificationChannel(
            APP_UPDATE_CHANNEL_ID,
            "App Updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Background app update downloads" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(appUpdateChannel)

        val libraryUpdateChannel = NotificationChannel(
            LIBRARY_UPDATE_CHANNEL_ID,
            "Library updates",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Background library refresh progress and results" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(libraryUpdateChannel)

        val ttsChannel = NotificationChannel(
            TTS_CHANNEL_ID,
            "Text-to-speech",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Read-aloud playback controls" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ttsChannel)

        // Carry old fixed-frequency prefs into the new count + unit model before
        // the schedulers read them, so an upgraded install keeps its schedule.
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            scheduleMigrator.migrateIfNeeded()
            // Re-key pre-existing libraries onto the package-derived source ids.
            runCatching { sourceIdMigrator.migrateIfNeeded() }
            backupScheduler.applyPreferredSchedule()
            libraryUpdateScheduler.applyPreferredSchedule()
            // Clear browse rows left stale by previous sessions on cold start.
            runCatching { transientNovelPruner.prune() }
        }
    }

    /**
     * Records uncaught exceptions to disk before the process dies, then hands
     * off to whatever handler was installed before us (the system handler that
     * shows the "app stopped" dialog and kills the process). The saved report is
     * surfaced on the next launch via [CrashLogStore.hasPendingCrash].
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { crashLogStore.save(throwable, thread) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val RELOCK_GRACE_MS = 30_000L

        const val DOWNLOAD_CHANNEL_ID = "downloads"
        const val BACKUP_CHANNEL_ID = "backups"
        const val APP_UPDATE_CHANNEL_ID = "app_updates"
        const val TTS_CHANNEL_ID = "tts"
        const val LIBRARY_UPDATE_CHANNEL_ID = "library_updates"
    }
}
