package io.grimoire.app.auth.github

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the user's stored GitHub access token to requests bound for GitHub
 * hosts. Hosts outside this allowlist (e.g. extension source sites returned by
 * an `index.json`) never see the token even if the token is present.
 *
 * If we get a 401 back from GitHub while a token was attached, the token has
 * been revoked or expired — clear it so the next attempt prompts the user to
 * reconnect rather than retrying with the dead credential.
 */
@Singleton
class GitHubAuthInterceptor @Inject constructor(
    private val store: GitHubAuthStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        val needsAuth = host in GITHUB_HOSTS && original.header("Authorization") == null
        val token = if (needsAuth) store.currentToken() else null

        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        val response = chain.proceed(request)
        if (token != null && response.code == 401) {
            store.clear()
        }
        return response
    }

    private companion object {
        val GITHUB_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "codeload.github.com",
        )
    }
}
