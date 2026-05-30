package io.grimoire.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import io.grimoire.app.data.backup.BackupScheduler
import io.grimoire.app.data.cache.CoverPreloader
import io.grimoire.app.data.libraryupdate.LibraryUpdateScheduler
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.di.GitHubAuthorized
import io.grimoire.app.extension.repo.ExtensionRepository
import io.grimoire.api.network.NetworkContext
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class GrimoireApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var hiddenAuthManager: HiddenCategoriesAuthManager
    @Inject lateinit var coverPreloader: CoverPreloader
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var backupScheduler: BackupScheduler
    @Inject lateinit var libraryUpdateScheduler: LibraryUpdateScheduler
    @Inject lateinit var extensionRepository: ExtensionRepository
    @Inject @GitHubAuthorized lateinit var imageHttpClient: OkHttpClient

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
            override fun onStop(owner: LifecycleOwner) {
                hiddenAuthManager.lock()
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

        backupScheduler.applyPreferredSchedule()
        libraryUpdateScheduler.applyPreferredSchedule()
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "downloads"
        const val BACKUP_CHANNEL_ID = "backups"
        const val APP_UPDATE_CHANNEL_ID = "app_updates"
        const val TTS_CHANNEL_ID = "tts"
        const val LIBRARY_UPDATE_CHANNEL_ID = "library_updates"
    }
}
