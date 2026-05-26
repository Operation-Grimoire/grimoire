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
import okhttp3.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class IndexAuthRequiredException(message: String, val statusCode: Int) : RuntimeException(message)

@Singleton
class ExtensionIndexFetcher @Inject constructor(
    @ApplicationContext context: Context,
    @GitHubAuthorized private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir = File(context.cacheDir, "ext_index").also { it.mkdirs() }

    private fun cacheFile(indexUrl: String) =
        File(cacheDir, indexUrl.hashCode().toString() + ".json")

    fun loadCached(indexUrl: String): List<RemoteExtension>? = runCatching {
        val file = cacheFile(indexUrl)
        if (!file.exists()) return null
        json.decodeFromString<List<RemoteExtension>>(file.readText())
    }.getOrNull()

    suspend fun fetch(indexUrl: String): Result<List<RemoteExtension>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = GitHubReleaseAsset.parse(indexUrl)
                    ?.let { fetchViaGitHubApi(it, indexUrl) }
                    ?: fetchDirect(indexUrl)
                val list = json.decodeFromString<List<RemoteExtension>>(body)
                cacheFile(indexUrl).writeText(body)
                list
            }
        }

    private fun fetchDirect(indexUrl: String): String {
        val request = Request.Builder().url(indexUrl).build()
        return client.newCall(request).execute().use { response ->
            checkSuccess(response, indexUrl)
            response.body!!.string()
        }
    }

    /**
     * GitHub serves private-repo release assets reliably through its REST
     * API. Walks two API hops:
     *  1. `/repos/{owner}/{repo}/releases/tags/{tag}` to look up the asset's
     *     numeric id (the only way; asset name isn't a valid lookup key on
     *     the assets endpoint).
     *  2. `/repos/{owner}/{repo}/releases/assets/{id}` with
     *     `Accept: application/octet-stream` to download. The interceptor
     *     attaches the Bearer token; OkHttp follows the 302 to the signed
     *     CDN URL (stripping Authorization on the cross-host hop, which is
     *     correct — the signed URL self-authenticates).
     */
    private fun fetchViaGitHubApi(asset: GitHubReleaseAsset, indexUrl: String): String {
        val releaseReq = Request.Builder()
            .url(asset.releaseByTagUrl())
            .header("Accept", "application/vnd.github+json")
            .build()
        val releaseJson = client.newCall(releaseReq).execute().use { resp ->
            checkSuccess(resp, asset.releaseByTagUrl())
            resp.body!!.string()
        }
        val release = json.decodeFromString<GitHubRelease>(releaseJson)
        val match = release.assets.firstOrNull { it.name == asset.name }
            ?: throw IndexAuthRequiredException(
                "Release ${asset.tag} has no asset named ${asset.name}.",
                statusCode = 404,
            )

        val assetReq = Request.Builder()
            .url(asset.assetDownloadUrl(match.id))
            .header("Accept", "application/octet-stream")
            .build()
        return client.newCall(assetReq).execute().use { resp ->
            checkSuccess(resp, indexUrl)
            resp.body!!.string()
        }
    }

    private fun checkSuccess(response: Response, requestedUrl: String) {
        if (response.isSuccessful) return
        val host = response.request.url.host
        if ((host == "github.com" || host == "api.github.com") &&
            (response.code == 401 || response.code == 404)
        ) {
            throw IndexAuthRequiredException(
                "HTTP ${response.code} for $requestedUrl — repo may be private or require sign-in.",
                statusCode = response.code,
            )
        }
        error("HTTP ${response.code} for $requestedUrl")
    }

    @Serializable
    private data class GitHubRelease(val assets: List<GitHubAsset> = emptyList())

    @Serializable
    private data class GitHubAsset(val id: Long, val name: String)
}
