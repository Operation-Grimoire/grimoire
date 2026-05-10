package io.grimoire.app.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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
) {
    private val _extensions = MutableStateFlow<List<LoadedExtension>>(emptyList())
    val extensions: StateFlow<List<LoadedExtension>> = _extensions.asStateFlow()

    init {
        // Eagerly scan installed extensions so the list is ready before the first refresh().
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) { scanPackages() }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { scanPackages() }

    private fun scanPackages() {
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
    }
}

private fun PackageManager.getInstalledPackagesCompat(): List<PackageInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getInstalledPackages(PackageManager.GET_META_DATA)
    }
