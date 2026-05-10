package io.grimoire.app.extension.repo

import io.grimoire.api.network.defaultOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionIndexFetcher @Inject constructor() {

    private val client = defaultOkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(indexUrl: String): Result<List<RemoteExtension>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(indexUrl).build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code} for $indexUrl" }
                    json.decodeFromString<List<RemoteExtension>>(response.body!!.string())
                }
            }
        }
}
