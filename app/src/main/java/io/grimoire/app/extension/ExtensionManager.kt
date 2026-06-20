package io.grimoire.app.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.source.ConfigurableSource
import io.grimoire.api.source.MultiHostSource
import io.grimoire.api.source.MultiLanguageSource
import io.grimoire.app.data.preferences.SourceSettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val TAG = "ExtensionManager"

@Singleton
class ExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: ExtensionLoader,
    private val sourceSettings: SourceSettingsPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()

    private val _extensions = MutableStateFlow<List<LoadedExtension>>(emptyList())
    val extensions: StateFlow<List<LoadedExtension>> = _extensions.asStateFlow()

    // Eagerly scan installed extensions so the list is ready before the first refresh().
    // Callers that read [extensions] near process start (workers, the reader resolving
    // its source) must await this first or they will observe an empty list and treat
    // every novel as "Source not installed". Failures are swallowed here (and logged)
    // so a thrown scan doesn't poison awaitReady() forever — refresh() rescans.
    private val initialScan: Deferred<Unit> = scope.async {
        runCatching { scanPackages() }
            .onFailure { Log.e(TAG, "Initial extension scan failed", it) }
        Unit
    }

    /** Suspends until the initial package scan has populated [extensions]. */
    suspend fun awaitReady() {
        initialScan.await()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        initialScan.await()
        scanPackages()
    }

    /** Re-reads persisted settings for [pkg] and pushes them into its source. */
    fun reapplyPreferences(pkg: String) {
        scope.launch {
            _extensions.value.firstOrNull { it.info.packageName == pkg }?.let { applyPreferences(it) }
        }
    }

    /**
     * Re-pushes settings into every loaded source. Used by the global content-
     * language picker so a global change applies to non-overriding sources
     * without restarting the app.
     */
    fun reapplyAllPreferences() {
        scope.launch {
            _extensions.value.forEach { applyPreferences(it) }
        }
    }

    private suspend fun applyPreferences(loaded: LoadedExtension) {
        val pkg = loaded.info.packageName
        (loaded.source as? ConfigurableSource)?.let { configurable ->
            val keys = configurable.getPreferences().map { it.key }
            configurable.setPreferences(sourceSettings.snapshot(pkg, keys))
        }
        (loaded.source as? MultiLanguageSource)?.setEnabledLanguages(
            sourceSettings.effectiveLanguages(pkg),
        )
        (loaded.source as? MultiHostSource)?.setActiveHost(sourceSettings.activeHostNow(pkg))
    }

    private suspend fun scanPackages() = scanMutex.withLock {
        val pm = context.packageManager
        val loaded = pm.getInstalledPackagesCompat().mapNotNull { pkg ->
            val meta = pkg.applicationInfo?.metaData ?: return@mapNotNull null
            if (!meta.getBoolean("grimoire.extension", false)) return@mapNotNull null
            val className = meta.getString("grimoire.extension.class")
                ?: return@mapNotNull null
            val info = ExtensionInfo(
                packageName = pkg.packageName,
                label = pm.getApplicationLabel(pkg.applicationInfo!!).toString(),
                versionName = pkg.versionName.orEmpty(),
                versionCode = PackageInfoCompat.getLongVersionCode(pkg),
                sourceClassName = className,
                apkPath = pkg.applicationInfo!!.sourceDir,
            )
            val source = loader.load(info) ?: return@mapNotNull null
            LoadedExtension(info, source)
        }
        // Ids are package-derived so a collision should be impossible; log loudly
        // if two ever hash alike rather than mis-attribute novels silently.
        loaded.groupBy { it.id }
            .filterValues { it.size > 1 }
            .forEach { (id, dupes) ->
                Log.e(TAG, "Source id collision $id: ${dupes.joinToString { it.info.packageName }}")
            }

        // Configure before publishing: consumers must never observe a source that
        // hasn't had its persisted login/host/language settings pushed in yet.
        loaded.forEach { applyPreferences(it) }
        _extensions.value = loaded
    }
}

private fun PackageManager.getInstalledPackagesCompat(): List<PackageInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getInstalledPackages(PackageManager.GET_META_DATA)
    }
