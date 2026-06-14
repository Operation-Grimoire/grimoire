package io.grimoire.app.auth.athenaeum

import android.os.Build
import io.grimoire.app.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the paired Athenaeum device token to requests bound for the Athenaeum
 * API host (only). A 401 means the token was revoked or expired server-side, so
 * we clear the store and the pairing screen drops to Disconnected.
 */
@Singleton
class AthenaeumAuthInterceptor @Inject constructor(
    private val store: AthenaeumAuthStore,
) : Interceptor {

    private val apiHost = BuildConfig.ATHENAEUM_API_BASE.toHttpUrlOrNull()?.host

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != apiHost) return chain.proceed(original)

        val token = store.currentToken()
        val builder = original.newBuilder().header("User-Agent", USER_AGENT)
        if (token != null && original.header("Authorization") == null) {
            builder.header("Authorization", "Bearer $token")
        }
        val response = chain.proceed(builder.build())
        if (token != null && response.code == 401) {
            store.clear()
        }
        return response
    }

    private companion object {
        // Identifies the paired device in the user's "paired devices" view.
        val USER_AGENT = "Grimoire/${BuildConfig.VERSION_NAME} (${Build.MANUFACTURER} ${Build.MODEL})"
    }
}
