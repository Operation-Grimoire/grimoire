package io.grimoire.app.extension.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.di.GitHubAuthorized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class IndexAuthRequiredException(message: String) : RuntimeException(message)

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
                val request = Request.Builder().url(indexUrl).build()
                client.newCall(request).execute().use { response ->
                    // A 401/404 from github.com almost always means "this is a
                    // private repo and you aren't authenticated" — GitHub uses
                    // 404 rather than 403 to avoid leaking the repo's existence.
                    if (!response.isSuccessful && isGitHubHost(request.url.host) &&
                        (response.code == 401 || response.code == 404)
                    ) {
                        throw IndexAuthRequiredException(
                            "HTTP ${response.code} for $indexUrl — repo may be private or require sign-in.",
                        )
                    }
                    check(response.isSuccessful) { "HTTP ${response.code} for $indexUrl" }
                    val body = response.body!!.string()
                    val list = json.decodeFromString<List<RemoteExtension>>(body)
                    cacheFile(indexUrl).writeText(body)
                    list
                }
            }
        }

    private fun isGitHubHost(host: String): Boolean =
        host == "github.com" || host == "api.github.com" || host == "codeload.github.com"
}
