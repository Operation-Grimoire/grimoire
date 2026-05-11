package io.grimoire.app.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val RELEASES_URL =
    "https://api.github.com/repos/Operation-Grimoire/grimoire/releases/latest"

@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val release = json.decodeFromString<GitHubRelease>(body)
            val tagVersion = release.tag_name.removePrefix("v")
            if (tagVersion != BuildConfig.VERSION_NAME) {
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                if (apk != null) ReleaseInfo(release.tag_name, release.body, apk.browser_download_url)
                else null
            } else null
        }.getOrNull()
    }

    suspend fun downloadAndInstall(apkUrl: String) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "grimoire-update.apk")
            val conn = URL(apkUrl).openConnection() as HttpURLConnection
            conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            conn.disconnect()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            context.startActivity(intent)
        }
    }
}
