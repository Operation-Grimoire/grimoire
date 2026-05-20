package io.grimoire.app.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.source.ConfigurableSource
import io.grimoire.api.source.MultiLanguageSource
import io.grimoire.app.data.preferences.SourceSettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Singleton
class ExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: ExtensionLoader,
    private val sourceSettings: SourceSettingsPreferences,
) {
    private val _extensions = MutableStateFlow<List<LoadedExtension>>(emptyList())
    val extensions: StateFlow<List<LoadedExtension>> = _extensions.asStateFlow()

    init {
        // Eagerly scan installed extensions so the list is ready before the first refresh().
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) { scanPackages() }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { scanPackages() }

    /** Re-reads persisted settings for [pkg] and pushes them into its source. */
    fun reapplyPreferences(pkg: String) {
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            _extensions.value.firstOrNull { it.info.packageName == pkg }?.let { applyPreferences(it) }
        }
    }

    /**
     * Re-pushes settings into every loaded source. Used by the global content-
     * language picker so a global change applies to non-overriding sources
     * without restarting the app.
     */
    fun reapplyAllPreferences() {
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
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
    }

    private suspend fun scanPackages() {
        val pm = context.packageManager
        _extensions.value = pm.getInstalledPackagesCompat().mapNotNull { pkg ->
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
        _extensions.value.forEach { applyPreferences(it) }
    }
}

private fun PackageManager.getInstalledPackagesCompat(): List<PackageInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getInstalledPackages(PackageManager.GET_META_DATA)
    }
