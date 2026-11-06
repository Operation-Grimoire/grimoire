package io.grimoire.app.extension.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.R
import io.grimoire.app.di.GitHubAuthorized
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

class HashMismatchException(
    val expected: String,
    val actual: String,
) : Exception("Downloaded APK hash did not match: expected $expected, got $actual")

@Singleton
class ExtensionInstaller internal constructor(
    private val cacheDir: File,
    private val client: OkHttpClient,
    private val httpErrorMessage: (Int) -> String,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        @GitHubAuthorized client: OkHttpClient,
    ) : this(context.cacheDir, client, { code ->
        AppLocale.wrap(context).getString(R.string.extension_download_failed_http, code)
    })

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun download(
        apkUrl: String,
        packageName: String,
        expectedSha256: String? = null,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(cacheDir, "$packageName.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            val req = Request.Builder()
                .url(apkUrl)
                .apply {
                    if (apkUrl.startsWith("https://api.github.com/")) {
                        header("Accept", "application/octet-stream")
                    }
                }
                .build()
            val direct = client.newCall(req).execute()
            val asset = GitHubReleaseAsset.parse(apkUrl)
            // Public APKs use direct downloads. Private release URLs can
            // reject browser-style auth, requiring the existing API path.
            val download = if ((direct.code == 401 || direct.code == 404) && asset != null) {
                direct.close()
                val apiRequest = Request.Builder()
                    .url(resolveAssetApiUrl(asset))
                    .header("Accept", "application/octet-stream")
                    .build()
                client.newCall(apiRequest).execute()
            } else {
                direct
            }
            download.use { response ->
                if (!response.isSuccessful) failResponse(
                    response,
                    httpErrorMessage(response.code),
                )
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
            if (!resp.isSuccessful) failResponse(resp, "HTTP ${resp.code} for ${asset.releaseByTagUrl()}")
            resp.body!!.string()
        }
        val release = json.decodeFromString<GitHubRelease>(body)
        val match = release.assets.firstOrNull { it.name == asset.name }
            ?: error("Release ${asset.tag} has no asset named ${asset.name}")
        return asset.assetDownloadUrl(match.id)
    }

    /**
     * Translates a failed GitHub response into a typed error. A 403 with the
     * rate-limit headers set becomes a [GitHubRateLimitException] so the UI can
     * prompt the user to connect GitHub (or wait) instead of showing a raw HTTP
     * code; everything else falls back to a generic failure.
     */
    private fun failResponse(resp: Response, message: String): Nothing {
        val host = resp.request.url.host
        val isGitHub = host == "github.com" || host == "api.github.com"
        val rateLimited = resp.header("X-RateLimit-Remaining") == "0" ||
            resp.header("Retry-After") != null
        if (isGitHub && resp.code == 403 && rateLimited) {
            throw GitHubRateLimitException("GitHub rate limit reached.")
        }
        error(message)
    }

    @Serializable
    private data class GitHubRelease(val assets: List<GitHubAsset> = emptyList())

    @Serializable
    private data class GitHubAsset(val id: Long, val name: String)
}
