package io.grimoire.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.grimoire.api.network.defaultOkHttpClient
import io.grimoire.app.auth.github.GitHubAuthInterceptor
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the [OkHttpClient] that attaches the user's GitHub access token (via
 * [GitHubAuthInterceptor]) when talking to GitHub hosts. Use this for the
 * extension-index fetcher and APK downloader so private-repo URLs work; do not
 * use it for arbitrary extension-source HTTP — those clients live inside the
 * extensions and must not see the token.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubAuthorized

@Module
@InstallIn(SingletonComponent::class)
object GitHubAuthModule {

    @Provides
    @Singleton
    @GitHubAuthorized
    fun provideAuthorizedHttpClient(interceptor: GitHubAuthInterceptor): OkHttpClient =
        defaultOkHttpClient().newBuilder()
            .addInterceptor(interceptor)
            .build()

    /**
     * Plain client for the device-flow handshake itself — the token doesn't
     * exist yet at that point, and routing the request through the authorized
     * client would just be a no-op + an extra interceptor on the chain.
     */
    @Provides
    @Singleton
    fun providePlainHttpClient(): OkHttpClient = defaultOkHttpClient()
}
