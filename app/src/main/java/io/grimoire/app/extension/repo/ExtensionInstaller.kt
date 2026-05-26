package io.grimoire.app.extension.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.di.GitHubAuthorized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

class HashMismatchException(
    val expected: String,
    val actual: String,
) : Exception("Downloaded APK hash did not match: expected $expected, got $actual")

@Singleton
class ExtensionInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @GitHubAuthorized private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun download(
        apkUrl: String,
        packageName: String,
        expectedSha256: String? = null,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // Private-repo APKs need to go through GitHub's REST API; rewrite the
            // browser-style URL to the API-style asset endpoint so the Bearer
            // token actually gets honoured. Public repos and non-GitHub URLs
            // keep the direct path.
            val effectiveUrl = GitHubReleaseAsset.parse(apkUrl)
                ?.let { resolveAssetApiUrl(it) }
                ?: apkUrl
            val file = File(context.cacheDir, "$packageName.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            val req = Request.Builder()
                .url(effectiveUrl)
                .apply {
                    if (effectiveUrl.startsWith("https://api.github.com/")) {
                        header("Accept", "application/octet-stream")
                    }
                }
                .build()
            client.newCall(req).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body!!
                val total = body.contentLength().coerceAtLeast(0L)
                var read = 0L
                onProgress(read, total)
                file.outputStream().buffered().use { out ->
                    body.byteStream().use { input ->
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
            }
            if (expectedSha256 != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                    file.delete()
                    throw HashMismatchException(expectedSha256, actual)
                }
            }
            file
        }
    }

    /** Look up the numeric asset id for [asset] and return its API download URL. */
    private fun resolveAssetApiUrl(asset: GitHubReleaseAsset): String {
        val req = Request.Builder()
            .url(asset.releaseByTagUrl())
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code} for ${asset.releaseByTagUrl()}" }
            resp.body!!.string()
        }
        val release = json.decodeFromString<GitHubRelease>(body)
        val match = release.assets.firstOrNull { it.name == asset.name }
            ?: error("Release ${asset.tag} has no asset named ${asset.name}")
        return asset.assetDownloadUrl(match.id)
    }

    @Serializable
    private data class GitHubRelease(val assets: List<GitHubAsset> = emptyList())

    @Serializable
    private data class GitHubAsset(val id: Long, val name: String)
}
