package io.grimoire.app.data.athenaeum

import io.grimoire.app.BuildConfig
import io.grimoire.app.di.AthenaeumAuthorized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Submits scraped observations to Athenaeum's public ingest endpoint. Uses the
 * [AthenaeumAuthorized] client so a paired device's token is attached (the
 * submission auto-promotes); unpaired, it still posts anonymously. Batches are
 * capped at [MAX_BATCH] to match the server.
 */
@Singleton
class AthenaeumClient @Inject constructor(
    @AthenaeumAuthorized private val client: OkHttpClient,
) {
    // explicitNulls=false so optional fields (releaseKind, chapter-only fields) drop out.
    private val json = Json { explicitNulls = false; encodeDefaults = true }
    private val jsonMedia = "application/json".toMediaType()
    private val base = BuildConfig.ATHENAEUM_API_BASE.trimEnd('/')

    suspend fun submit(observations: List<ObservationItem>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            observations.chunked(MAX_BATCH).forEach { batch ->
                val body = json.encodeToString(SubmitObservationsRequest(batch)).toRequestBody(jsonMedia)
                val req = Request.Builder().url("$base/ingest/observations").post(body).build()
                client.newCall(req).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code} from /ingest/observations" }
                }
            }
        }
    }

    private companion object {
        const val MAX_BATCH = 100
    }
}
