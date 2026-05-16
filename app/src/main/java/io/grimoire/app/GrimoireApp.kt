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
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.api.network.NetworkContext
import javax.inject.Inject

@HiltAndroidApp
class GrimoireApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var hiddenAuthManager: HiddenCategoriesAuthManager
    @Inject lateinit var coverPreloader: CoverPreloader
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var backupScheduler: BackupScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
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

        backupScheduler.applyPreferredSchedule()
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "downloads"
        const val BACKUP_CHANNEL_ID = "backups"
    }
}
