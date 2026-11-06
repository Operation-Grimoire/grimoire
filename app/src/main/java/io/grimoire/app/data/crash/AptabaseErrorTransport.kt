package io.grimoire.app.data.crash

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal enum class ErrorDelivery { ACCEPTED, DROP, RETRY }

internal fun aptabaseErrorEndpoint(appKey: String): String? {
    val parts = appKey.trim().uppercase(Locale.ROOT).split('-')
    if (parts.size != 3 || parts[0] != "A" || !parts[2].matches(Regex("[A-Z0-9]+"))) return null
    return when (parts[1]) {
        "EU" -> "https://eu.aptabase.com/api/v0/error"
        "US" -> "https://us.aptabase.com/api/v0/error"
        else -> null
    }
}

internal fun errorDelivery(status: Int): ErrorDelivery = when {
    status == 202 -> ErrorDelivery.ACCEPTED
    status == 408 || status == 429 || status in 500..599 -> ErrorDelivery.RETRY
    else -> ErrorDelivery.DROP // Includes invalid key/payload and 403 quota exhaustion.
}

internal class AptabaseErrorTransport(
    private val appKey: String,
    private val endpoint: String? = aptabaseErrorEndpoint(appKey),
) {
    // Deliberately separate from source/GitHub clients: no auth, cookies or
    // interceptors. A redirect must never forward the app key to another host.
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { encodeDefaults = true }

    suspend fun send(report: AptabaseErrorReport): ErrorDelivery {
        val url = endpoint ?: return ErrorDelivery.DROP
        val request = Request.Builder().url(url).header("App-Key", appKey.trim())
            .post(json.encodeToString(report).toRequestBody("application/json".toMediaType()))
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(ErrorDelivery.RETRY)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { continuation.resume(errorDelivery(it.code)) }
                }
            })
        }
    }
}

/** Return true only when WorkManager should retry; re-read consent before each request. */
internal suspend fun drainAptabaseErrors(
    queue: AptabaseErrorQueue,
    allowed: suspend () -> Boolean,
    send: suspend (AptabaseErrorReport) -> ErrorDelivery,
): Boolean {
    if (!allowed()) { queue.clear(); return false }
    for ((id, report) in queue.pending()) {
        currentCoroutineContext().ensureActive()
        if (!allowed()) { queue.clear(); return false }
        when (send(report)) {
            ErrorDelivery.ACCEPTED, ErrorDelivery.DROP -> queue.remove(id)
            ErrorDelivery.RETRY -> return true
        }
    }
    return false
}
