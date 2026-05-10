package io.grimoire.app.extension.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.network.defaultOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionIndexFetcher @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val client = defaultOkHttpClient()
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
                    check(response.isSuccessful) { "HTTP ${response.code} for $indexUrl" }
                    val body = response.body!!.string()
                    val list = json.decodeFromString<List<RemoteExtension>>(body)
                    cacheFile(indexUrl).writeText(body)
                    list
                }
            }
        }
}
