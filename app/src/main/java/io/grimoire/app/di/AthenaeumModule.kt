package io.grimoire.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.grimoire.api.network.defaultOkHttpClient
import io.grimoire.app.auth.athenaeum.AthenaeumAuthInterceptor
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the [OkHttpClient] that attaches the Athenaeum device token (via
 * [AthenaeumAuthInterceptor]) for calls to the Athenaeum API. Use it for the
 * ingest client; never for arbitrary extension-source HTTP.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AthenaeumAuthorized

@Module
@InstallIn(SingletonComponent::class)
object AthenaeumModule {

    @Provides
    @Singleton
    @AthenaeumAuthorized
    fun provideAuthorizedClient(interceptor: AthenaeumAuthInterceptor): OkHttpClient =
        defaultOkHttpClient().newBuilder().addInterceptor(interceptor).build()

    // The device-flow handshake (/device/code, /device/token) is unauthenticated,
    // so AthenaeumDeviceAuth takes the plain client. The app already provides a
    // default OkHttpClient (used by GitHubDeviceAuth); AthenaeumDeviceAuth's
    // @Inject constructor receives that same binding.
}
