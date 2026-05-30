package io.grimoire.app.extension.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.di.GitHubAuthorized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class IndexAuthRequiredException(message: String, val statusCode: Int) : RuntimeException(message)

/**
 * Thrown when GitHub answers a 403 because the request budget is exhausted.
 * Unauthenticated callers get 60 requests/hour shared across index fetches,
 * icon loads and APK downloads; once spent every GitHub call 403s until reset.
 * Connecting a GitHub account lifts the ceiling to 5,000/hour.
 */
class GitHubRateLimitException(message: String) : RuntimeException(message)

@Singleton
class ExtensionIndexFetcher @Inject constructor(
    @ApplicationContext context: Context,
    @GitHubAuthorized private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
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
                val list = GitHubReleaseAsset.parse(indexUrl)
                    ?.let { fetchViaGitHubApi(it, indexUrl) }
                    ?: fetchDirect(indexUrl)
                cacheFile(indexUrl).writeText(
                    json.encodeToString(ListSerializer(RemoteExtension.serializer()), list),
                )
                list
            }
        }

    private fun fetchDirect(indexUrl: String): List<RemoteExtension> {
        val request = Request.Builder().url(indexUrl).build()
        return client.newCall(request).execute().use { response ->
            checkSuccess(response, indexUrl)
            json.decodeFromString(response.body!!.string())
        }
    }

    /**
     * GitHub serves private-repo release assets reliably through its REST
     * API. Walks two API hops:
     *  1. `/repos/{owner}/{repo}/releases/tags/{tag}` to enumerate the
     *     release's assets (id + name for each).
     *  2. `/repos/{owner}/{repo}/releases/assets/{id}` with
     *     `Accept: application/octet-stream` to download the index.
     *
     * While we have the assets list in hand we also rewrite every browser-
     * style download URL inside the index (APK url + iconUrl) to its
     * api.github.com asset URL. Downstream consumers — Coil for icons, the
     * APK installer, the cache — then transparently hit endpoints that
     * actually accept Bearer auth on private repos.
     */
    private fun fetchViaGitHubApi(asset: GitHubReleaseAsset, indexUrl: String): List<RemoteExtension> {
        val release = fetchRelease(asset)
        val indexAsset = release.assets.firstOrNull { it.name == asset.name }
            ?: throw IndexAuthRequiredException(
                "Release ${asset.tag} has no asset named ${asset.name}.",
                statusCode = 404,
            )
        val indexBody = downloadAsset(asset, indexAsset.id, indexUrl)
        val raw: List<RemoteExtension> = json.decodeFromString(indexBody)
        val byName = release.assets.associateBy { it.name }
        return raw.map { it.rewriteGitHubUrls(asset, byName) }
    }

    private fun fetchRelease(asset: GitHubReleaseAsset): GitHubRelease {
        val req = Request.Builder()
            .url(asset.releaseByTagUrl())
            .header("Accept", "application/vnd.github+json")
            .build()
        return client.newCall(req).execute().use { resp ->
            checkSuccess(resp, asset.releaseByTagUrl())
            json.decodeFromString(resp.body!!.string())
        }
    }

    private fun downloadAsset(asset: GitHubReleaseAsset, assetId: Long, indexUrl: String): String {
        val req = Request.Builder()
            .url(asset.assetDownloadUrl(assetId))
            .header("Accept", "application/octet-stream")
            .build()
        return client.newCall(req).execute().use { resp ->
            checkSuccess(resp, indexUrl)
            resp.body!!.string()
        }
    }

    private fun RemoteExtension.rewriteGitHubUrls(
        source: GitHubReleaseAsset,
        assetsByName: Map<String, GitHubAsset>,
    ): RemoteExtension = copy(
        url = rewriteAssetUrl(url, source, assetsByName) ?: url,
        iconUrl = iconUrl?.let { rewriteAssetUrl(it, source, assetsByName) ?: it },
    )

    private fun rewriteAssetUrl(
        url: String,
        source: GitHubReleaseAsset,
        assetsByName: Map<String, GitHubAsset>,
    ): String? {
        val parsed = GitHubReleaseAsset.parse(url) ?: return null
        // Only rewrite within the same release we just fetched; an
        // index.json that points at a different repo's release stays
        // untouched.
        if (parsed.owner != source.owner || parsed.repo != source.repo || parsed.tag != source.tag) {
            return null
        }
        val match = assetsByName[parsed.name] ?: return null
        return source.assetDownloadUrl(match.id)
    }

    private fun checkSuccess(response: Response, requestedUrl: String) {
        if (response.isSuccessful) return
        val host = response.request.url.host
        val isGitHub = host == "github.com" || host == "api.github.com"
        if (isGitHub && response.code == 403 && response.isRateLimited()) {
            throw GitHubRateLimitException("GitHub rate limit reached for $requestedUrl.")
        }
        if (isGitHub && (response.code == 401 || response.code == 404)) {
            throw IndexAuthRequiredException(
                "HTTP ${response.code} for $requestedUrl — repo may be private or require sign-in.",
                statusCode = response.code,
            )
        }
        error("HTTP ${response.code} for $requestedUrl")
    }

    /**
     * A 403 from GitHub means rate limiting when the remaining-requests header
     * is exhausted (primary limit) or a secondary-limit Retry-After is present.
     */
    private fun Response.isRateLimited(): Boolean =
        header("X-RateLimit-Remaining") == "0" || header("Retry-After") != null

    @Serializable
    private data class GitHubRelease(val assets: List<GitHubAsset> = emptyList())

    @Serializable
    private data class GitHubAsset(val id: Long, val name: String)
}
