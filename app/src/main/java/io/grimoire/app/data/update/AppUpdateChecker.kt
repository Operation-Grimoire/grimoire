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
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val REPO = "Operation-Grimoire/grimoire"
private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
private const val BETA_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/tags/beta"
private const val RELEASES_LIST_URL = "https://api.github.com/repos/$REPO/releases?per_page=30"
private const val APK_MIME = "application/vnd.android.package-archive"
private const val MAX_AGGREGATED_RELEASES = 10

class AppUpdateHashMismatchException(
    val expected: String,
    val actual: String,
) : Exception("Downloaded APK hash did not match: expected $expected, got $actual")

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
            val sha256Asset = release.assets.firstOrNull { it.name.endsWith(".apk.sha256") }
            val sha256 = sha256Asset?.let { asset ->
                fetchText(asset.browser_download_url)
                    ?.trim()
                    ?.substringBefore(' ')
                    ?.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
            }
            ReleaseInfo(
                tagName = release.tag_name,
                displayVersion = release.name.ifBlank { release.tag_name }
                    .removePrefix("Grimoire").trim().removePrefix("v"),
                releaseNotes = release.body,
                apkUrl = apk.browser_download_url,
                isPrerelease = release.prerelease,
                sha256 = sha256,
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

    private fun fetchReleases(url: String): List<GitHubRelease>? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().readText()
                json.decodeFromString<List<GitHubRelease>>(body)
            } else null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchStableNotesSince(fromTag: String, toTag: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val from = fromTag.removePrefix("v")
                val to = toTag.removePrefix("v")
                val releases = fetchReleases(RELEASES_LIST_URL) ?: return@runCatching null
                val included = releases.asSequence()
                    .filter { !it.prerelease && it.tag_name.isNotBlank() }
                    .map { it to it.tag_name.removePrefix("v") }
                    .filter { (_, name) ->
                        compareSemver(name, from) > 0 && compareSemver(name, to) <= 0
                    }
                    .sortedWith(compareByDescending { (_, name) -> SemverKey(name) })
                    .take(MAX_AGGREGATED_RELEASES)
                    .toList()
                if (included.isEmpty()) return@runCatching null
                included.joinToString("\n\n") { (release, _) ->
                    val header = "## ${release.tag_name}"
                    if (release.body.isBlank()) header else "$header\n\n${release.body.trim()}"
                }
            }.getOrNull()
        }

    suspend fun fetchStableNotesForVersion(toName: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val tag = if (toName.startsWith("v")) toName else "v$toName"
                fetchRelease("https://api.github.com/repos/$REPO/releases/tags/$tag")
                    ?.body
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    suspend fun fetchBetaNotesForSha(expectedSha: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (expectedSha.isBlank()) return@runCatching null
                val release = fetchRelease(BETA_RELEASE_URL) ?: return@runCatching null
                if (release.target_commitish.isBlank()) return@runCatching null
                if (release.target_commitish != expectedSha) return@runCatching null
                release.body.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    private data class SemverKey(val name: String) : Comparable<SemverKey> {
        private val parts: List<Int> = name.substringBefore('-').substringBefore('+')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }

        override fun compareTo(other: SemverKey): Int {
            val len = maxOf(parts.size, other.parts.size)
            for (i in 0 until len) {
                val a = parts.getOrElse(i) { 0 }
                val b = other.parts.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }
            return 0
        }
    }

    private fun compareSemver(a: String, b: String): Int = SemverKey(a).compareTo(SemverKey(b))

    private fun fetchText(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().readText()
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

    suspend fun download(
        apkUrl: String,
        expectedSha256: String? = null,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "grimoire-update.apk")
            val conn = URL(apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            try {
                val code = conn.responseCode
                check(code == HttpURLConnection.HTTP_OK) { "Download failed: HTTP $code" }
                val total = conn.contentLengthLong.coerceAtLeast(0L)
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                onProgress(read, total)
                conn.inputStream.use { input ->
                    file.outputStream().buffered().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            read += n
                            onProgress(read, total)
                        }
                    }
                }
                if (expectedSha256 != null) {
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expectedSha256, ignoreCase = true)) {
                        file.delete()
                        throw AppUpdateHashMismatchException(expectedSha256, actual)
                    }
                }
                file
            } finally {
                conn.disconnect()
            }
        }
    }

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    fun launchInstall(file: File) {
        context.startActivity(installIntent(file))
    }
}
