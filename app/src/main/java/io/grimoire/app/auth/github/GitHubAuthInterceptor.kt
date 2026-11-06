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
 * A 401 while a token was attached normally clears the stored credential.
 * Browser-style release downloads are the exception: private repos may reject
 * Bearer auth there, so retain the token for the authenticated API fallback.
 */
@Singleton
class GitHubAuthInterceptor @Inject constructor(
    private val store: GitHubAuthStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        if (host !in GITHUB_HOSTS) return chain.proceed(original)

        val token = if (original.header("Authorization") == null) {
            store.currentToken()
        } else {
            null
        }

        val builder = original.newBuilder()
        if (token != null) {
            builder.header("Authorization", "Bearer $token")
        }
        // GitHub serves release-asset bytes only when the request accepts
        // application/octet-stream. Two URL shapes hit this path:
        //  - github.com/{owner}/{repo}/releases/download/{tag}/{name}
        //    (browser-style; used by public-repo index entries and any pre-
        //    rewrite stragglers).
        //  - api.github.com/repos/{owner}/{repo}/releases/assets/{id}
        //    (API style; what ExtensionIndexFetcher rewrites to for private
        //    repos, and what Coil hits for icon loads).
        val path = original.url.encodedPath
        val isBrowserAssetFetch = host == "github.com" && path.contains("/releases/download/")
        val isAssetFetch = isBrowserAssetFetch ||
            (host == "api.github.com" && path.startsWith("/repos/") && path.contains("/releases/assets/"))
        if (isAssetFetch && original.header("Accept") == null) {
            builder.header("Accept", "application/octet-stream")
        }

        val response = chain.proceed(builder.build())
        if (token != null && response.code == 401 && !isBrowserAssetFetch) {
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
