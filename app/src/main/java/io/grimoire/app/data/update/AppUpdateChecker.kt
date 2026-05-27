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
private const val RELEASES_LIST_URL = "https://api.github.com/repos/$REPO/releases?per_page=30"
private const val APK_MIME = "application/vnd.android.package-archive"
private const val MAX_AGGREGATED_RELEASES = 10
private val BETA_TAG_REGEX = Regex("""^v.+-beta\..+""")

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
            val release = when (channel) {
                UpdateChannel.STABLE -> fetchRelease(LATEST_RELEASE_URL)
                UpdateChannel.BETA -> latestBetaRelease()
            } ?: return@runCatching null
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
        fetchAggregatedNotes(fromTag, toTag) { !it.prerelease }

    suspend fun fetchBetaNotesSince(fromTag: String, toTag: String): String? =
        fetchAggregatedNotes(fromTag, toTag) { it.prerelease && BETA_TAG_REGEX.matches(it.tag_name) }

    suspend fun fetchNotesForVersion(toName: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val tag = if (toName.startsWith("v")) toName else "v$toName"
                fetchRelease("https://api.github.com/repos/$REPO/releases/tags/$tag")
                    ?.body
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    private suspend fun fetchAggregatedNotes(
        fromTag: String,
        toTag: String,
        filter: (GitHubRelease) -> Boolean,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val from = fromTag.removePrefix("v")
            val to = toTag.removePrefix("v")
            val releases = fetchReleases(RELEASES_LIST_URL) ?: return@runCatching null
            val included = releases.asSequence()
                .filter { it.tag_name.isNotBlank() && filter(it) }
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

    private fun latestBetaRelease(): GitHubRelease? {
        val releases = fetchReleases(RELEASES_LIST_URL) ?: return null
        return releases.asSequence()
            .filter { it.prerelease && BETA_TAG_REGEX.matches(it.tag_name) }
            .maxByOrNull { SemverKey(it.tag_name.removePrefix("v")) }
    }

    internal data class SemverKey(val name: String) : Comparable<SemverKey> {
        private val coreString: String = name.substringBefore('+')
        private val baseParts: List<Int> = coreString.substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
        private val prereleaseParts: List<PrereleasePart> =
            coreString.substringAfter('-', missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() }
                ?.split('.')
                ?.map { token ->
                    token.toIntOrNull()?.let { PrereleasePart.Numeric(it) }
                        ?: PrereleasePart.Alpha(token)
                }
                ?: emptyList()
        private val isPrerelease: Boolean = prereleaseParts.isNotEmpty()

        override fun compareTo(other: SemverKey): Int {
            val len = maxOf(baseParts.size, other.baseParts.size)
            for (i in 0 until len) {
                val a = baseParts.getOrElse(i) { 0 }
                val b = other.baseParts.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }
            // Same base. Semver: a release without prerelease > one with.
            if (!isPrerelease && other.isPrerelease) return 1
            if (isPrerelease && !other.isPrerelease) return -1
            val pl = maxOf(prereleaseParts.size, other.prereleaseParts.size)
            for (i in 0 until pl) {
                val a = prereleaseParts.getOrNull(i)
                val b = other.prereleaseParts.getOrNull(i)
                if (a == null) return -1
                if (b == null) return 1
                val cmp = a.compareTo(b)
                if (cmp != 0) return cmp
            }
            return 0
        }

        sealed class PrereleasePart : Comparable<PrereleasePart> {
            data class Numeric(val value: Int) : PrereleasePart()
            data class Alpha(val value: String) : PrereleasePart()

            override fun compareTo(other: PrereleasePart): Int = when {
                this is Numeric && other is Numeric -> value.compareTo(other.value)
                this is Alpha && other is Alpha -> value.compareTo(other.value)
                // Semver: numeric identifiers always have lower precedence than alphanumeric.
                this is Numeric -> -1
                else -> 1
            }
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
            UpdateChannel.BETA -> compareSemver(
                release.tag_name.removePrefix("v"),
                BuildConfig.VERSION_NAME,
            ) > 0
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
