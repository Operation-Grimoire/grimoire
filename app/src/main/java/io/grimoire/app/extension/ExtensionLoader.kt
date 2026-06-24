package io.grimoire.app.extension

import android.content.Context
import dalvik.system.PathClassLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.source.Source
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads an extension's [Source] from its installed APK and **caches** the result.
 *
 * ## Why the cache exists
 *
 * [ExtensionManager.scanPackages] re-loads every installed extension on every
 * rescan, and the app rescans *often* — returning to the extensions screen,
 * opening browse, toggling a source, and each library sync all trigger one.
 *
 * A [PathClassLoader] is not cheap: it maps the APK's dex and pulls in the
 * extension's classes (and their static state) on first class load. Minting a
 * fresh one per scan churned a new class loader + source instance every time;
 * the discarded ones hold loaded `Class` objects and native dex mappings that
 * the GC reclaims slowly, so under heavy navigation they piled up faster than
 * they were collected and eventually exhausted the heap — surfacing as an
 * OutOfMemoryError on whatever thread next allocated (often the OkHttp HTTP/2
 * reader mid-download).
 *
 * Caching by package — invalidated only when the APK's version or path changes
 * (an update/reinstall) — bounds this to **one class loader per installed
 * extension** for the life of the process. Reuse is safe: a [Source] is already
 * long-lived (the same instance sits in [ExtensionManager.extensions] between
 * scans), and per-scan settings are re-pushed onto it by
 * [ExtensionManager.applyPreferences] regardless of whether it was freshly
 * loaded or served from cache.
 */
@Singleton
class ExtensionLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private class Cached(
        val versionCode: Long,
        val apkPath: String,
        val sourceClassName: String,
        val source: Source,
    )

    private val lock = Any()
    private val cache = HashMap<String, Cached>()

    fun load(info: ExtensionInfo): Source? = synchronized(lock) {
        cache[info.packageName]?.let { cached ->
            if (cached.versionCode == info.versionCode &&
                cached.apkPath == info.apkPath &&
                cached.sourceClassName == info.sourceClassName
            ) {
                return cached.source
            }
        }
        val source = runCatching {
            val classLoader = PathClassLoader(info.apkPath, context.classLoader)
            classLoader.loadClass(info.sourceClassName)
                .getDeclaredConstructor()
                .newInstance() as Source
        }.getOrNull() ?: return null
        cache[info.packageName] = Cached(
            versionCode = info.versionCode,
            apkPath = info.apkPath,
            sourceClassName = info.sourceClassName,
            source = source,
        )
        source
    }

    /**
     * Drops cached loaders for packages no longer present in [packageNames] (an
     * extension was uninstalled), so their class loaders become collectable
     * instead of lingering for the life of the process.
     */
    fun retainOnly(packageNames: Set<String>) = synchronized(lock) {
        cache.keys.retainAll(packageNames)
    }
}
