package io.grimoire.app.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.BuildConfig
import io.grimoire.app.data.preferences.UpdateChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val REPO = "Operation-Grimoire/grimoire"
private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
private const val BETA_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/tags/beta"

@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(channel: UpdateChannel): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val url = when (channel) {
                UpdateChannel.STABLE -> LATEST_RELEASE_URL
                UpdateChannel.BETA -> BETA_RELEASE_URL
            }
            val release = fetchRelease(url) ?: return@runCatching null
            if (!isUpdate(release, channel)) return@runCatching null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@runCatching null
            ReleaseInfo(
                tagName = release.tag_name,
                displayVersion = release.name.ifBlank { release.tag_name.removePrefix("v") },
                releaseNotes = release.body,
                apkUrl = apk.browser_download_url,
                isPrerelease = release.prerelease,
            )
        }.getOrNull()
    }

    private fun fetchRelease(url: String): GitHubRelease? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().readText()
                json.decodeFromString<GitHubRelease>(body)
            } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun isUpdate(release: GitHubRelease, channel: UpdateChannel): Boolean {
        return when (channel) {
            UpdateChannel.STABLE -> release.tag_name.removePrefix("v") != BuildConfig.VERSION_NAME
            // The beta tag is rolling, so compare the build commit SHA instead of the tag name.
            UpdateChannel.BETA -> {
                val remoteSha = release.target_commitish
                val localSha = BuildConfig.GIT_SHA
                remoteSha.isNotBlank() && localSha.isNotBlank() && remoteSha != localSha
            }
        }
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
